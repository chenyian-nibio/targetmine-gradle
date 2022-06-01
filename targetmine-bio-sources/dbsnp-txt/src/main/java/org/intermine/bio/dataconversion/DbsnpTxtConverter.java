package org.intermine.bio.dataconversion;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
    
	private static final String HUMAN_TAXON_ID = "9606";
	
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
    	
		if (tableType.equals("table1")) { // used to be "info"
			System.out.println("Processing SNP information......");
			processDbsnpTable1(reader);
		} else if (tableType.equals("table3")) { // used to be "gene" 
			System.out.println("Processing SNP gene association......");
			processDbsnpTable3(reader);
		} else if (tableType.equals("table2")) { // used to be "transcript" 
			System.out.println("Processing SNP transcript association......");
			processDbsnpTable2(reader);
		} else if (tableType.equals("table4")) { // alternative format of table2 
			System.out.println("Processing SNP transcript association......");
			processDbsnpTable4(reader);
		} else {
			System.out.println("No matched table type. Finish the processing.");
		}
    }
    
    /**
     * Table1 here is a preprocessed tab separated table for the snp basic information. 
     * The columns are as follow:  <br/>
     * id (without rs); chromosome; position(start from 0); pmid(separated by semicolon); alleles info(details see below) <br/>
     * 
     * alleles info, separated by pipe. the columns are as follow: <br/>
     * delete,insert,hgvs expression,expression type </br>
     * 
     * e.g. 10082476 10 122405137 19961953;24013816 A,C,NC_000010.11:g.122405138A>C,spdi|A,G,NC_000010.11:g.122405138A>G,spdi
     * 
     * @param reader
     * @throws Exception
     */
	private void processDbsnpTable1(Reader reader) throws Exception {
		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
		int line = 1;
		int storedSnp = 0;
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			String rsId = "rs" + cols[0];
			String pubmedIdString = cols[3];
			if (snpIdSet.contains(rsId) || !StringUtils.isEmpty(pubmedIdString)) {
				String chr = cols[1];
				String locationString = cols[2];
				String alleleString = cols[4];
				
				Item item = createItem("SNP");
				item.setAttribute("primaryIdentifier", rsId);
				if (!StringUtils.isEmpty(alleleString)) {
					List<String> alleleList = new ArrayList<String>();
					for (String allele : alleleString.split("\\|")) {
						String[] parts = allele.split(",", -1);
						alleleList.add(String.format("%s>%s", parts[0], parts[1]));
					};
					item.setAttribute("refSnpAllele", StringUtils.join(alleleList, " / "));
				}
				if (!StringUtils.isEmpty(locationString)) {
					// check if the locationString is an integer
					if (isValidId(locationString)) {
						Integer position = Integer.valueOf(locationString) + 1;
						
						item.setAttribute("position", String.format("%s:%d", chr, position));
						
						item.setAttribute("coordinate", position.toString());
						
						if (!StringUtils.isEmpty(chr)) {
							String chrRef = getChromosome(HUMAN_TAXON_ID, chr);
							item.setReference("chromosome", chrRef);
							
							Item location = createItem("Location");
							location.setAttribute("start", String.valueOf(position));
							location.setAttribute("end", String.valueOf(position));
							if (chrRef != null) {
								location.setReference("locatedOn", chrRef);
							}
							location.setReference("feature", item);
							store(location);
							item.setReference("chromosomeLocation", location);
						}
						
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
				item.setReference("organism", getOrganism(HUMAN_TAXON_ID));
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
	
	private void processDbsnpTable3(Reader reader) throws Exception {
		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
		int count = 1;
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			String snpId = "rs" + cols[0];
			if (snpIdSet.contains(snpId)) {
				String geneId = cols[1];
				String soId = cols[2];
				if (soId.contains(";")) {
					LOG.info(String.format("%s contains multiple SO: %s", snpId, soId));
					soId = soId.split(";")[0];
				} 

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

	private void processDbsnpTable2(Reader reader) throws Exception {
		Map<String, String> variAnnotMap = new HashMap<String, String>();
		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
		int count = 1;
		int countRef = 0;
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			if (12 != cols.length) {
				continue;
			}
			String snpId = "rs" + cols[0];
			if (snpIdSet.contains(snpId)) {
				String geneId = cols[1];
				String mrnaAcc = cols[2];
				String hgvs = cols[3];
				String codon = cols[4];
				String proteinAcc = cols[5];
				String residue = cols[6];
//				String expressionType = cols[8];
				String mrnaPos = cols[9];
				String allele = cols[10];
				String aaPos = cols[11];

				String soId = cols[7];
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
				createSNPReference(mrnaAcc, mrnaPos, hgvs, allele, codon, proteinAcc, aaPos, residue, soId, vaItemRef);
				countRef++;
			}
			count++;
			if (count % 5000000 == 0) {
				System.out.println(String.format("Process %d lines...", count));
			}
		}
		System.out.println(String.format("Create %d SNPReference(s).", countRef));
	}

	private void processDbsnpTable4(Reader reader) throws Exception {
		Map<String, String> variAnnotMap = new HashMap<String, String>();
		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
		int count = 1;
		int countRef = 0;
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			if (3 != cols.length) {
				System.out.println("ERROR: " + StringUtils.join(cols, ","));
				continue;
			}
			String snpId = "rs" + cols[0];
			if (snpIdSet.contains(snpId)) {
				String geneId = cols[1];
				String transcriptInfo = cols[2];
				
				for (String transcript : transcriptInfo.split("\\|")) {
					String[] parts = transcript.split(",", -1);
					if (10 != parts.length) {
						System.out.println("ERROR: " + transcript);
						continue;
					}
					
					String mrnaAcc = parts[0];
					String hgvs = parts[1];
					String codon = parts[2];
					String proteinAcc = parts[3];
					String residue = parts[4];
//					String expressionType = parts[6];
					String mrnaPos = parts[7];
					String allele = parts[8];
					String aaPos = parts[9];
					
					String soId = parts[5];
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
					createSNPReference(mrnaAcc, mrnaPos, hgvs, allele, codon, proteinAcc, aaPos, residue, soId, vaItemRef);
					countRef++;
				}
			}
			count++;
			if (count % 5000000 == 0) {
				System.out.println(String.format("Process %d lines...", count));
			}
		}
		System.out.println(String.format("Create %d SNPReference(s).", countRef));
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
			item.setAttribute("primaryIdentifier", identifier);
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

	private String createSNPReference(String mrnaAcc, String mrnaPos, String hgvs,
			String allele, String codon, String proteinAcc, String aaPos, String residue,
			String soId, String vaItemRef) throws ObjectStoreException {
		Item item = createItem("SNPReference");
		item.setAttribute("mrnaAccession", mrnaAcc);
		item.setAttributeIfNotNull("mrnaPosition", mrnaPos);
		item.setAttributeIfNotNull("change", hgvs);
		if (!StringUtils.isEmpty(allele) && !allele.equals("----")) {
			item.setAttribute("mrnaAllele", allele);
		}
		if (!StringUtils.isEmpty(codon) && !codon.equals("---")) {
			item.setAttribute("mrnaCodon", codon);
		}
		if (!StringUtils.isEmpty(proteinAcc) && !proteinAcc.equals("0")) {
			item.setAttribute("proteinAccession", proteinAcc);
		}
		item.setAttributeIfNotNull("proteinPosition", aaPos);
		item.setAttributeIfNotNull("residue", residue);
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

		QueryField qfSnpId = new QueryField(qcSnp, "primaryIdentifier");

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
	
	private Map<String, String> chromosomeMap = new HashMap<String, String>();
	private String getChromosome(String taxonId, String identifier) throws ObjectStoreException {
		String key = taxonId + "-" + identifier;
		String ret = chromosomeMap.get(key);
		if (ret == null) {
			Item chromosome = createItem("Chromosome");
			chromosome.setReference("organism", getOrganism(taxonId));
			chromosome.setAttribute("primaryIdentifier", identifier);
			chromosome.setAttribute("symbol", identifier);
			store(chromosome);
			ret = chromosome.getIdentifier();
			chromosomeMap.put(key, ret);
		}
		return ret;
	}

}
