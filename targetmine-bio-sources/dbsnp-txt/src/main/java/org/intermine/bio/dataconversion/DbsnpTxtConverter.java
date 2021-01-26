package org.intermine.bio.dataconversion;

import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
public class DbsnpTxtConverter extends BioFileConverter
{
	private static final Logger LOG = LogManager.getLogger(DbsnpTxtConverter.class);
	//
    private static final String DATASET_TITLE = "dbSNP";
    private static final String DATA_SOURCE_NAME = "NCBI";
    
//	private static final int HUMAN_TAXON_ID = 9606;
	
    Set<String> snpIdSet;
    
//	private boolean subsetOnly = false;
    private String tableType = "";
    public void setTableType(String tableType) {
    	this.tableType = tableType;
    }

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public DbsnpTxtConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
		if (snpIdSet == null) {
			snpIdSet = new HashSet<String>();
			System.out.println("Get stored SNP ids ......");
			getSnpIds();
			LOG.info(String.format("Found %d SNPs", snpIdSet.size()));
		}
		System.out.println(String.format("Found %d SNPs", snpIdSet.size()));
    	
		if (tableType.equals("info")) {
			System.out.println("Processing SNP information......");
			processDbsnpInfo(reader);
		} else if (tableType.equals("gene")) {
			System.out.println("Processing SNP gene association......");
			processDbsnpGene(reader);
		} else if (tableType.equals("transcript")) {
			System.out.println("Processing SNP transcript association......");
			processDbsnpTranscript(reader);
		}
    }
    
	private void processDbsnpInfo(Reader reader) throws Exception {
		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
		int line = 1;
		int storedSnp = 0;
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			String rsId = "rs" + cols[0];
			String pubmedIdString = cols[4];
			if (snpIdSet.contains(rsId) || !StringUtils.isEmpty(pubmedIdString)) {
				String allele = cols[1];
				String chr = cols[2];
				String locationString = cols[3];
				
				Item item = createItem("SNP");
				item.setAttribute("identifier", rsId);
				if (!StringUtils.isEmpty(chr)) {
					item.setAttribute("chromosome", chr);
				}
				if (!StringUtils.isEmpty(allele)) {
					item.setAttribute("refSnpAllele", allele);
				}
				if (!StringUtils.isEmpty(locationString)) {
					item.setAttribute("location", locationString);
					String pos = locationString.substring(locationString.indexOf(":") + 1);
					// check if the pos string is an integer
					if (isValidId(pos)) {
						item.setAttribute("coordinate", pos);
					}
				}
				if (!StringUtils.isEmpty(pubmedIdString)) {
					String[] ids = pubmedIdString.split(";");
					for (String id : ids) {
						if (isValidId(id)) {
							item.addToCollection("publications", getPublication(id));
						}
					}
				}
				store(item);
				storedSnp++;
			}
			line++;
			
			if (line % 5000000 == 0) {
				System.out.println(String.format("Process %d lines...", line));
			}
		}
		System.out.println("Finish reading the file...");
		
		System.out.println(String.format("Stored %d SNPs", storedSnp));
		LOG.info(String.format("Stored %d SNPs", storedSnp));
	}
	
	private void processDbsnpGene(Reader reader) throws Exception {
		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
		int count = 1;
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			String snpId = "rs" + cols[0];
			if (snpIdSet.contains(snpId)) {
				String geneId = cols[1];
				String soId = cols[2];
				Item vaItem = createItem("VariationAnnotation");
				vaItem.setAttribute("identifier", snpId + "-" + geneId);
				vaItem.setReference("gene", getGene(geneId));
				vaItem.setReference("snp", getSnp(snpId));
				if (!StringUtils.isEmpty(soId)) {
					vaItem.setReference("function", getSOTerm(soId));
				}
				store(vaItem);
			}
			count++;
			if (count % 5000000 == 0) {
				System.out.println(String.format("Process %d lines...", count));
			}
		}
	}

	private void processDbsnpTranscript(Reader reader) throws Exception {
		Map<String, String> variAnnotMap = new HashMap<String, String>();
		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
		int count = 1;
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			if (11 != cols.length) {
				continue;
			}
			String snpId = "rs" + cols[0];
			if (snpIdSet.contains(snpId)) {
				String geneId = cols[1];
				String mrnaAcc = cols[2];
				String mrnaPos = cols[3];
				String orientation = cols[4];
				String allele = cols[5];
				String codon = cols[6];
				String proteinAcc = cols[7];
				String aaPos = cols[8];
				String residue = cols[9];

				String soId = cols[10];
				if (soId.contains(";")) {
					LOG.info(String.format("%s contains multiple SO: %s", snpId, soId));
					soId = soId.split(";")[0];
				} 
				
				String key = snpId + "-" + geneId;
				String vaItemRef = variAnnotMap.get(key);
				if (vaItemRef == null) {
					Item vaItem = createItem("VariationAnnotation");
					vaItem.setAttribute("identifier", key);
					if (!StringUtils.isEmpty(soId)) {
						vaItem.setReference("function", getSOTerm(soId));
					}
					store(vaItem);
					vaItemRef = vaItem.getIdentifier();
					variAnnotMap.put(key, vaItemRef);
				}
				createSNPReference(mrnaAcc, mrnaPos, orientation, allele, codon, proteinAcc, aaPos, residue, soId, vaItemRef);
			}
			count++;
			if (count % 5000000 == 0) {
				System.out.println(String.format("Process %d lines...", count));
			}
		}
	}
	
	private Map<String, String> geneMap = new HashMap<String, String>();
	private Map<String, String> snpMap = new HashMap<String, String>();
	private Map<String, String> publicationMap = new HashMap<String, String>();
	private String getGene(String primaryIdentifier) throws ObjectStoreException {
		String ret = geneMap.get(primaryIdentifier);
		if (ret == null) {
			Item item = createItem("Gene");
			item.setAttribute("primaryIdentifier", primaryIdentifier);
			store(item);
			ret = item.getIdentifier();
			geneMap.put(primaryIdentifier, ret);
		}
		return ret;
	}
	private String getSnp(String identifier) throws ObjectStoreException {
		String ret = snpMap.get(identifier);
		if (ret == null) {
			Item item = createItem("SNP");
			item.setAttribute("identifier", identifier);
			store(item);
			ret = item.getIdentifier();
			snpMap.put(identifier, ret);
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

	private String createSNPReference(String mrnaAcc, String mrnaPos, String orientation,
			String allele, String codon, String proteinAcc, String aaPos, String residue,
			String soId, String vaItemRef) throws ObjectStoreException {
		Item item = createItem("SNPReference");
		item.setAttribute("mrnaAccession", mrnaAcc);
		if (!StringUtils.isEmpty(mrnaPos)) {
			item.setAttribute("mrnaPosition", mrnaPos);
		}
		if (!StringUtils.isEmpty(orientation)) {
			item.setAttribute("orientation", orientation);
		}
		if (!StringUtils.isEmpty(allele)) {
			item.setAttribute("mrnaAllele", allele);
		}
		if (!StringUtils.isEmpty(codon) && !codon.equals("---")) {
			item.setAttribute("mrnaCodon", codon);
		}
		if (!StringUtils.isEmpty(proteinAcc) && !proteinAcc.equals("0")) {
			item.setAttribute("proteinAccession", proteinAcc);
		}
		if (!StringUtils.isEmpty(aaPos)) {
			item.setAttribute("proteinPosition", aaPos);
		}
		if (!StringUtils.isEmpty(residue)) {
			item.setAttribute("residue", residue);
		}
		if (!StringUtils.isEmpty(soId)) {
			item.setReference("function", getSOTerm(soId));
		}
		item.setReference("annotation", vaItemRef);
		store(item);

		return item.getIdentifier();
	}

	private String osAlias = null;

	public void setOsAlias(String osAlias) {
		this.osAlias = osAlias;
	}

	@SuppressWarnings("unchecked")
	private void getSnpIds() throws Exception {
		ObjectStore os = ObjectStoreFactory.getObjectStore(osAlias);

		Query q = new Query();
		QueryClass qcSnp = new QueryClass(os.getModel().getClassDescriptorByName("SNP").getType());

		QueryField qfSnpId = new QueryField(qcSnp, "identifier");

		q.addFrom(qcSnp);
		q.addToSelect(qfSnpId);

		Results results = os.execute(q);
		Iterator<Object> iterator = results.iterator();
		while (iterator.hasNext()) {
			ResultsRow<String> rr = (ResultsRow<String>) iterator.next();
			snpIdSet.add(rr.get(0));
		}
	}

	/**
	 * check if the string s is a positive integer 
	 * @param s
	 * @return
	 */
	public static boolean isValidId(String s) {
		if (s == null || s.isEmpty())
			return false;
		for (int i = 0; i < s.length(); i++) {
			if (i == 0 && s.charAt(i) == '0') {
				return false;
			}
			if (Character.digit(s.charAt(i), 10) < 0)
				return false;
		}
		return true;
	}

	private Map<String, String> soMap = new HashMap<String, String>();
	private String getSOTerm(String soId) throws ObjectStoreException {
		String ret = soMap.get(soId);
		if (ret == null) {
			Item item = createItem("SOTerm");
			item.setAttribute("identifier", soId);
			store(item);
			ret = item.getIdentifier();
			soMap.put(soId, ret);
		}
		return ret;
	}
}
