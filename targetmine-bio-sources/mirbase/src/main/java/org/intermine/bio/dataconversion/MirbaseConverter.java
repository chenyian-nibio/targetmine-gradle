package org.intermine.bio.dataconversion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreFactory;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

/**
 * 
 * @author chenyian
 */
public class MirbaseConverter extends BioFileConverter {
	// private static Logger LOG = Logger.getLogger(MirbaseConverter.class);
	//
	private static final String DATASET_TITLE = "miRBase";
	private static final String DATA_SOURCE_NAME = "miRBase";

	private Map<String, String> taxonIdMap;
	private Map<String, String> geneMap = new HashMap<String, String>();
	private Map<String, String> publicationMap = new HashMap<String, String>();
	private Map<String, String> miRNAMap = new HashMap<String, String>();

	private Set<String> taxonIds = new HashSet<String>();
	
	public void setOrganisms(String taxonIdString) {
		for (String taxonId : StringUtils.split(taxonIdString, " ")) {
			taxonIds.add(taxonId);
		}
	}
	
	/**
	 * Constructor
	 * 
	 * @param writer
	 *            the ItemWriter used to handle the resultant items
	 * @param model
	 *            the Model
	 */
	public MirbaseConverter(ItemWriter writer, Model model) {
		super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
	}

	/**
	 * 
	 * 
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		System.out.println("processing taxonId.txt file...");
		readTaxonIdMap();
		
		System.out.println("querying for miRNA ids...");
		getMiRNAIdMaps();
		
		System.out.println("processing mature.fa file...");
		processMatureFa();

		System.out.println("processing miRNA.dat file...");
		processMirnaDat(reader);
	}

	private File taxonIdFile;

	public void setTaxonIdFile(File taxonIdFile) {
		this.taxonIdFile = taxonIdFile;
	}

	private File matureFaFile;
	
	public void setMatureFaFile(File matureFaFile) {
		this.matureFaFile = matureFaFile;
	}

	private void readTaxonIdMap() throws Exception {
		taxonIdMap = new HashMap<String, String>();
		FileReader fileReader = new FileReader(taxonIdFile);
		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(fileReader);
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			taxonIdMap.put(cols[1], cols[0]);
		}
	}

	private void processMatureFa() throws Exception {
		FileReader fileReader = new FileReader(matureFaFile);
		BufferedReader br = new BufferedReader(fileReader);
		String line;
		while ((line = br.readLine()) != null) {
			if (line.startsWith(">")) {
				// >hsa-let-7a-5p MIMAT0000062 Homo sapiens let-7a-5p
				Pattern pattern = Pattern.compile(">(.+) (MIMAT\\d+) (.+)");
				Matcher matcher = pattern.matcher(line);
				if (matcher.matches()) {
					String symbol = matcher.group(1);
					String accession = matcher.group(2);
					String name = matcher.group(3);
					String seq = br.readLine();
					String taxonId = getTaxonIdBySymbol(symbol);
					if (taxonId == null || !taxonIds.contains(taxonId)) {
						continue;
					}

					if (miRNAIdMap.get(accession) == null) {
						Item item = createItem("MiRNA");
						item.setAttribute("primaryIdentifier", accession);
//						item.setAttribute("secondaryIdentifier", accession);
						item.setAttribute("name", name);
						item.setAttribute("symbol", symbol);
						item.setReference("sequence", createSequence(seq));
						item.setAttribute("length", String.valueOf(seq.length()));
						item.setReference("organism", getOrganism(taxonId));
						store(item);
						miRNAMap.put(accession, item.getIdentifier());
					} else {
						for (String pid : miRNAIdMap.get(accession)) {
							Item item = createItem("MiRNA");
							item.setAttribute("primaryIdentifier", pid);
							item.setAttribute("secondaryIdentifier", accession);
							item.setAttribute("name", name);
							item.setAttribute("symbol", symbol);
							item.setReference("sequence", createSequence(seq));
							item.setAttribute("length", String.valueOf(seq.length()));
							item.setReference("organism", getOrganism(taxonId));
							store(item);
							miRNAMap.put(pid, item.getIdentifier());
						}
					}
				}
			}
		}
		br.close();
	}

	private String getTaxonIdBySymbol(String identifier) {
		String taxonId = taxonIdMap.get(identifier.substring(0, identifier.indexOf("-")));
		if (taxonId != null) {
			return taxonId;
		} else {
			throw new RuntimeException("unexpected organism code: " + identifier.substring(0, 3)
					+ " from entry: " + identifier);
		}
	}

	private void processMirnaDat(Reader reader) throws Exception {
		BufferedReader in = null;
		in = new BufferedReader(reader);
		String line;
		MicroRNAEntry entry = new MicroRNAEntry();
		while ((line = in.readLine()) != null) {
			// process
			if (line.startsWith("ID")) {
				// ID hsa-let-7a-1 standard; RNA; HSA; 80 BP.
				Pattern pattern = Pattern.compile("ID\\s+(.+?)\\s+\\w+; \\w+; \\w+; \\d+ BP\\.");
				Matcher matcher = pattern.matcher(line);
				if (matcher.matches()) {
					entry.symbol = matcher.group(1);
				}
			} else if (line.startsWith("AC")) {
				entry.accession = line.substring(5, 14);
			} else if (line.startsWith("DE")) {
				entry.description = line.substring(5);
			} else if (line.startsWith("RX")) {
				entry.pubMedIds.add(line.substring(line.indexOf(";") + 2, line.indexOf(".")));
			} else if (line.startsWith("FT")) {
				String substring = line.substring(line.indexOf("/") + 1);
				if (substring.startsWith("accession")) {
					entry.matures.add(substring.substring(11, substring.length() - 1));
				}
			} else if (line.startsWith("DR")) {
				// DR ENTREZGENE; 406881; MIRLET7A1.
				// DR ENTREZGENE; 100422902; MIR3116-1.
				Pattern pattern = Pattern.compile("DR\\s+ENTREZGENE; (\\d+); (.+)\\.");
				Matcher matcher = pattern.matcher(line);
				if (matcher.matches()) {
					entry.geneId = matcher.group(1);
				}
			} else if (line.startsWith("//")) {
				// create MicroRNA
				createMicroRNA(entry);
				entry = new MicroRNAEntry();
			} else if (line.startsWith("  ")) {
				entry.sequence += line;
			}
		}
		in.close();
	}

	private void createMicroRNA(MicroRNAEntry entry) throws ObjectStoreException {
		String taxonId = getTaxonIdBySymbol(entry.symbol);
		if (taxonId == null || !taxonIds.contains(taxonId)) {
			return;
		}
		
		Item item = createItem("MiRNAPrimaryTranscript");
		item.setAttribute("primaryIdentifier", entry.accession);
//		item.setAttribute("secondaryIdentifier", entry.symbol);
		item.setAttribute("name", entry.description);
		item.setAttribute("symbol", entry.symbol);
		item.setReference("sequence", createSequence(entry.getSequence()));
		item.setAttribute("length", String.valueOf(entry.getSequence().length()));
		if (entry.geneId != null) {
			item.setReference("gene", getGene(entry.geneId));
		}
		for (String pubmedId : entry.pubMedIds) {
			item.addToCollection("publications", getPublication(pubmedId));
		}
		for (String mature : entry.matures) {
			if (miRNAIdMap.get(mature) == null) {
				item.addToCollection("microRNAs", getMiRNA(mature));
			} else {
				for (String id : miRNAIdMap.get(mature)) {
					item.addToCollection("microRNAs", getMiRNA(id));
				}
			}
		}
		item.setReference("organism", getOrganism(taxonId));
		store(item);
	}

	private String createSequence(String sequence) throws ObjectStoreException {
		Item item = createItem("Sequence");
		item.setAttribute("residues", sequence);
		item.setAttribute("length", String.valueOf(sequence.length()));
		store(item);
		return item.getIdentifier();
	}

	private String getMiRNA(String identifier) throws ObjectStoreException {
		String ret = miRNAMap.get(identifier);
		if (ret == null) {
			Item item = createItem("MiRNA");
			item.setAttribute("primaryIdentifier", identifier);
			store(item);
			ret = item.getIdentifier();
			miRNAMap.put(identifier, ret);
		}
		return ret;
	}

	private String getGene(String ncbiGeneId) throws ObjectStoreException {
		String ret = geneMap.get(ncbiGeneId);
		if (ret == null) {
			Item item = createItem("Gene");
			item.setAttribute("primaryIdentifier", ncbiGeneId);
			item.setAttribute("ncbiGeneId", ncbiGeneId);
			store(item);
			ret = item.getIdentifier();
			geneMap.put(ncbiGeneId, ret);
		}

		return ret;

	}

	private String getPublication(String pubmedId) throws ObjectStoreException {
		String ret = publicationMap.get(pubmedId);
		if (ret == null) {
			Item item = createItem("Publication");
			item.setAttribute("pubMedId", pubmedId);
			store(item);
			ret = item.getIdentifier();
			publicationMap.put(pubmedId, ret);
		}
		return ret;
	}

	private static class MicroRNAEntry {
		String geneId;
		String accession;
		String description;
		String sequence = "";
		Set<String> pubMedIds = new HashSet<String>();
		Set<String> matures = new HashSet<String>();
		String symbol;

		public MicroRNAEntry() {
		}

		@Override
		public String toString() {
			return StringUtils.join(
					Arrays.asList(symbol, accession, geneId, getSequence(),
							pubMedIds.toString(), matures.toString()), "\t");
		}

		public String getSequence() {
			return sequence.replaceAll("\\d+", "").replaceAll("\\s+", "");
		}
	}

	private String osAlias = null;

	public void setOsAlias(String osAlias) {
		this.osAlias = osAlias;
	}
	
	private Map<String, Set<String>> miRNAIdMap;

	@SuppressWarnings("unchecked")
	private void getMiRNAIdMaps() throws Exception {
		ObjectStore os = ObjectStoreFactory.getObjectStore(osAlias);

		Query q = new Query();
		QueryClass qcSnp = new QueryClass(os.getModel().getClassDescriptorByName("MiRNA").getType());

		QueryField qfPrimaryId = new QueryField(qcSnp, "primaryIdentifier");
		QueryField qfSecondaryId = new QueryField(qcSnp, "secondaryIdentifier");

		q.addFrom(qcSnp);
		q.addToSelect(qfPrimaryId);
		q.addToSelect(qfSecondaryId);

		Results results = os.execute(q);
		Iterator<Object> iterator = results.iterator();
		miRNAIdMap = new HashMap<String, Set<String>>();
		while (iterator.hasNext()) {
			ResultsRow<String> rr = (ResultsRow<String>) iterator.next();
			String second = rr.get(1);
			if (StringUtils.isEmpty(second)) {
				continue;
			}
			if (miRNAIdMap.get(second) == null) {
				miRNAIdMap.put(second, new HashSet<String>());
			}
			miRNAIdMap.get(second).add(rr.get(0));
		}
	}

}
