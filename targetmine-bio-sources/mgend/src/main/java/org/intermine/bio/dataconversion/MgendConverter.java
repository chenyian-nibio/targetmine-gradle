package org.intermine.bio.dataconversion;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;


/**
 * 
 * @author chenyian
 */
public class MgendConverter extends BioFileConverter {
	private static final Logger LOG = LogManager.getLogger(MgendConverter.class);
	//
    private static final String DATASET_TITLE = "MGeND";
    private static final String DATA_SOURCE_NAME = "MGeND";
    
    private static Map<String, Integer> ageGroupMap = new HashMap<String, Integer>();
    private static Map<String, Integer> genderGroupMap = new HashMap<String, Integer>();
    {
    	ageGroupMap.put("0-9", 12);
    	ageGroupMap.put("10-19", 14);
    	ageGroupMap.put("20-29", 16);
    	ageGroupMap.put("30-39", 18);
    	ageGroupMap.put("40-49", 20);
    	ageGroupMap.put("50-59", 22);
    	ageGroupMap.put("60-69", 24);
    	ageGroupMap.put("70-79", 26);
    	ageGroupMap.put("80-89", 28);
    	ageGroupMap.put("90-99", 30);
    	ageGroupMap.put("100-", 32);
    	ageGroupMap.put("unknown", 34);
    	ageGroupMap.put("not provided", 36);
    	
    	genderGroupMap.put("male", 38);
    	genderGroupMap.put("female", 40);
    	genderGroupMap.put("mixed gender", 42);
    	genderGroupMap.put("unknown", 44);
    	genderGroupMap.put("not provided", 46);
    }

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public MgendConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
    	if (mimMedgenMap.isEmpty()) {
    		readMimMedgenFile();
    	}
    	if (medgenUidMap.isEmpty()) {
    		readMedgenUidFile();
    	}
		if (resolver == null) {
			resolver = new UMLSResolver(mrConsoFile, mrStyFile);
		}
    	
    	Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
    	
    	while (iterator.hasNext()) {
    		String[] cols = iterator.next();
    		
    		String rsid = cols[0];
    		String variantRef;
    		if (!rsid.equals("-")) {
    			// if rsid exits, create an SNP using rsid
				variantRef = getSnp(rsid);
    			
    		} else {
        		// otherwise, have to create the item and mapping ourselves  
        		// if only single position, create an SNP, else create a Variant 
        		// in addition, we need VariationAnnotation
        		// 1. be careful for redundant
        		// 2. what would be the identifier?
        		// 3. multiple mapping; 
    			
    			String snpId = String.format("%s:%s-%d", cols[3], cols[5], Integer.valueOf(cols[6]) - Integer.valueOf(cols[5]) + 1);
    			
    			variantRef = snpMap.get(snpId);
    			if (variantRef == null) {
    				Item item;
    				String chr = cols[3].substring(3);
    				String location;
    				if (cols[5].equals(cols[6])) {
    					item = createItem("SNP");
    					location = String.format("%s:%s", chr, cols[5]);
    				} else {
    					item = createItem("Variant");
    					location = String.format("%s:%s-%s", chr, cols[5], cols[6]);
    				}
    				item.setAttribute("identifier", snpId);
    				item.setAttribute("chromosome", chr);
    				item.setAttribute("coordinate", cols[5]);
    				item.setAttribute("location", location);
    				
    				store(item);
    				variantRef = item.getIdentifier();
    				snpMap.put(snpId, variantRef);
    				
    				if (!cols[1].equals("-")) {
    					String[] geneIds = cols[1].split(";");
    					for (String geneId : geneIds) {
    						String identifier = String.format("%s-%s", snpId, geneId);
    						Item va = createItem("VariationAnnotation");
    						va.setAttribute("identifier", identifier);
    						va.setReference("gene", getGene(geneId));
    						va.setReference("snp", item);
    						store(va);
    					}
    				}
    			}
    			
    		}
    		
    		Item item = createItem("Mgend");
    		item.setReference("variant", variantRef);
    		item.setAttribute("alleleChange", String.format("%s->%s", cols[7], cols[8]));
    		if (!StringUtils.isEmpty(cols[10])) {
    			item.setAttribute("aminoAcidChange", cols[10]);
    		}
    		item.setAttribute("clinicalSignificance", cols[11]);
    		if (!StringUtils.isEmpty(cols[54])) {
    			item.setAttribute("diseaseType", cols[54]);
    		}
    		if (!StringUtils.isEmpty(cols[55])) {
    			item.setAttribute("cellType", cols[55]);
    		}


    		String diseaseName = "";
    		if (!StringUtils.isEmpty(cols[52])) {
    			diseaseName = cols[52];
    		} else if (!StringUtils.isEmpty(cols[53])) {
    			diseaseName = cols[53];
    		}
    		if (!StringUtils.isEmpty(diseaseName)) {
    			item.setAttribute("diseaseName", diseaseName);
    		}

    		if (cols[50].equals("MeSH")) {
    			if (!StringUtils.isEmpty(cols[51])) {
    				item.setReference("diseaseTerm", getMeshTerm(cols[51]));
    			}
    		} else if (cols[50].equals("MedGen")) {
    			if (!StringUtils.isEmpty(cols[51])) {
    				String medgenId = medgenUidMap.get(cols[51]);
    				if (medgenId != null) {
    					item.setReference("diseaseTerm", getMedgenTerm(medgenId));
    				}
    			}
    		} else if (cols[50].equals("OMIM")) {
    			if (!StringUtils.isEmpty(cols[51])) {
    				String medgenId = mimMedgenMap.get(cols[51].substring(2));
					if (medgenId != null) {
						item.setReference("diseaseTerm", getMedgenTerm(medgenId));
    				}
    			}
    		} else if (!StringUtils.isEmpty(diseaseName)) {
    			String cui = getCui(diseaseName);
    			if (!StringUtils.isEmpty(cui)) {
    				item.setReference("diseaseTerm", getUmlsTerm(cui));
    			}
    		}
    		
    		store(item);

    		for (String key : ageGroupMap.keySet()) {
				int index = ageGroupMap.get(key).intValue();
				if (!cols[index].equals("0")) {
					Item num = createItem("CaseNumber");
					num.setAttribute("type", "age");
					num.setAttribute("title", key);
					num.setAttribute("number", cols[index]);
					num.setReference("mgend", item);
					store(num);
				}
			}
    		
    		for (String key : genderGroupMap.keySet()) {
				int index = genderGroupMap.get(key).intValue();
				if (!cols[index].equals("0")) {
					Item num = createItem("CaseNumber");
					num.setAttribute("type", "gender");
					num.setAttribute("title", key);
					num.setAttribute("number", cols[index]);
					num.setReference("mgend", item);
					store(num);
				}
			}
    		
    	}
    }


	private Map<String, String> geneMap = new HashMap<String, String>();
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
	
	private Map<String, String> snpMap = new HashMap<String, String>();
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

	private File mimMedgenFile;
	private File medgenUidFile;
	
	public void setMimMedgenFile(File mimMedgenFile) {
		this.mimMedgenFile = mimMedgenFile;
	}
	public void setMedgenUidFile(File medgenUidFile) {
		this.medgenUidFile = medgenUidFile;
	}
	
	private Map<String, String> mimMedgenMap = new HashMap<String, String>();
	private void readMimMedgenFile() throws Exception {
		FileReader reader = new FileReader(mimMedgenFile);
		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			
			String omimId = cols[0];
			String medGeneCui = cols[4];
			
			if (!StringUtils.isEmpty(medGeneCui)) {
				mimMedgenMap.put(omimId, medGeneCui);
			}
		}
	}

	private Map<String, String> medgenUidMap = new HashMap<String, String>();
	private void readMedgenUidFile() throws Exception {
		FileReader reader = new FileReader(medgenUidFile);
		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			
			String uid = cols[0];
			String medGeneCui = cols[1];
			
			if (!StringUtils.isEmpty(medGeneCui)) {
				medgenUidMap.put(uid, medGeneCui);
			}
		}
	}

	private Map<String, String> medgenTermMap = new HashMap<String, String>();
	private String getMedgenTerm(String identifier) throws ObjectStoreException {
		String ret = medgenTermMap.get(identifier);
		if (ret == null) {
			// TODO rename to MedgenTerm in the future
			Item item = createItem("DiseaseTerm");
			item.setAttribute("identifier", identifier);
			store(item);
			ret = item.getIdentifier();
			medgenTermMap.put(identifier, ret);
		}
		return ret;
	}

	private Map<String, String> meshTermMap = new HashMap<String, String>();
	private String getMeshTerm(String meshId) throws ObjectStoreException {
		String ret = meshTermMap.get(meshId);
		if (ret == null) {
			Item item = createItem("MeshTerm");
			item.setAttribute("identifier", meshId);
			store(item);
			ret = item.getIdentifier();
			meshTermMap.put(meshId, ret);
		}
		return ret;
	}

	private Map<String, String> umlsTermMap = new HashMap<String, String>();
	private String getUmlsTerm(String cui) throws ObjectStoreException {
		String ret = umlsTermMap.get(cui);
		if (ret == null) {
			Item item = createItem("UMLSTerm");
			item.setAttribute("identifier", "UMLS:" + cui);
			store(item);
			ret = item.getIdentifier();
			umlsTermMap.put(cui, ret);
		}
		return ret;
	}

	private UMLSResolver resolver;

	private File mrConsoFile;
	private File mrStyFile;

	public void setMrConsoFile(File mrConsoFile) {
		this.mrConsoFile = mrConsoFile;
	}

	public void setMrStyFile( File mrStyFile ) {
		this.mrStyFile = mrStyFile;
	}

	private Map<String, String> diseaseTermCuiMap = new HashMap<String, String>();
	private String getCui(String diseaseName) {
		String ret = diseaseTermCuiMap.get(diseaseName);
		if (ret == null) {
			ret = resolver.getIdentifier(diseaseName);
		
			if (ret == null && diseaseName.contains("(disorder)")) {
				ret = resolver.getIdentifier(diseaseName.replaceAll("\\(disorder\\)", "").trim());
			}
			
			if (ret == null) {
				ret = "";
				LOG.info("Cannot find CUI for the term: '" + diseaseName + "'");
			}
			diseaseTermCuiMap.put(diseaseName, ret);
		}
		return ret;
	}

}
