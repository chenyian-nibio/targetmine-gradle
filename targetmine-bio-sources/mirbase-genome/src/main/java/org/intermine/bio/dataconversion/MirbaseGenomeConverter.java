package org.intermine.bio.dataconversion;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

/**
 * 
 * @author chenyian
 */
public class MirbaseGenomeConverter extends BioFileConverter {
	// private static Logger LOG = Logger.getLogger(MirbaseGenomeConverter.class);
	//
	private static final String DATASET_TITLE = "miRBase";
	private static final String DATA_SOURCE_NAME = "miRBase";

	private Map<String, String> chromosomeMap = new HashMap<String, String>();

	private Map<String, String> accessionMap = new HashMap<String, String>();

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
	public MirbaseGenomeConverter(ItemWriter writer, Model model) {
		super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
	}

	/**
	 * 
	 * 
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		if (taxonIdMap == null) {
			readTaxonIdMap();
		}
		
		String currentTaxonId = getTaxonIdByFilename(getCurrentFile().getName());

		// no need to incorporate all chromosome data
		if (taxonIds.contains(currentTaxonId)) {
			Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
			while (iterator.hasNext()) {
				String[] cols = iterator.next();
				String chr = cols[0];
				String type = cols[2];
				
				String start = cols[3];
				String end = cols[4];
				String strand = cols[6];
				
				Map<String, String> ids = new HashMap<String, String>();
				for (String pair : cols[8].split(";")) {
					String[] kv = pair.split("=");
					ids.put(kv[0], kv[1]);
				}
				String accession = ids.get("ID");
				if (accession.contains("_") || accessionMap.get(accession) != null) {
					continue;
				}
				
				Item item;
				if (type.equals("miRNA")) {
					item = createItem("MiRNA");
				} else if (type.equals("miRNA_primary_transcript")) {
					item = createItem("MiRNAPrimaryTranscript");
				} else {
					throw new RuntimeException("Unexpect type: " + type);
				}
				item.setAttribute("primaryIdentifier", accession);
				
				String refId = item.getIdentifier();
				
				accessionMap.put(accession, refId);
				String chromosomeRefId = getChromosome(chr, currentTaxonId);
				
				Item location = createItem("Location");
				location.setAttribute("start", start);
				location.setAttribute("end", end);
				location.setAttribute("strand", strand.equals("+") ? "1" : "-1");
				location.setReference("feature", refId);
				location.setReference("locatedOn", chromosomeRefId);
				
				item.setReference("chromosome", chromosomeRefId);
				item.setReference("chromosomeLocation", location);
				
				store(location);
				store(item);
			}
		}
	}

	private String getChromosome(String chr, String taxonId) throws ObjectStoreException {
		String key = chr + ":" + taxonId;
		String ret = chromosomeMap.get(key);
		if (ret == null) {
			Item item = createItem("Chromosome");
			String chrId = chr;
			if (chr.toLowerCase().startsWith("chr")) {
				chrId = chr.substring(3);
			}
			item.setAttribute("primaryIdentifier", chrId);
			item.setAttribute("symbol", chrId);
			if (!StringUtils.isEmpty(taxonId)) {
				item.setReference("organism", getOrganism(taxonId));
			}
			store(item);
			ret = item.getIdentifier();
			chromosomeMap.put(key, ret);
		}
		return ret;
	}

	private String getTaxonIdByFilename(String fileName) {
		String taxonId = taxonIdMap.get(fileName.substring(0, fileName.indexOf(".")));
		if (taxonId != null) {
			return taxonId;
		} else {
			throw new RuntimeException("unexpected organism code: " + fileName.substring(0, 3)
					+ " from file: " + fileName);
		}
	}

	private Map<String, String> taxonIdMap;

	private File taxonIdFile;

	public void setTaxonIdFile(File taxonIdFile) {
		this.taxonIdFile = taxonIdFile;
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

}
