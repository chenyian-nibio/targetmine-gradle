package org.intermine.bio.dataconversion;

import java.io.File;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.xml.full.Item;


/**
 * 
 * @author mss-uehara-san (create)
 * @author chenyian (refine)
 */
public class IpfTrialConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "IPF Trial";
    private static final String DATA_SOURCE_NAME = "IPF";

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public IpfTrialConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    private static Map<String, String> trialPropertyNames = new HashMap<String, String>();
    private static Map<String, String> markerPropertyNames = new HashMap<String, String>();
    
    static {
    	// trialPropertyNames
        trialPropertyNames.put("referenceType","reference_type");
        trialPropertyNames.put("chembl","ChEMBL");
  		trialPropertyNames.put("therapy","drug/therapy");
  		trialPropertyNames.put("referenceTherapy","reference_drug/therapy");
  		trialPropertyNames.put("treatmentDetails","treatment_details (Seperator '\\\\')");
  		trialPropertyNames.put("dose","dose");
  		trialPropertyNames.put("routeOfAdministration","route of administration");
  		trialPropertyNames.put("duration","duration");
  		trialPropertyNames.put("approvedDrug","approved_drug");
        trialPropertyNames.put("approvalAutority","approval_authority");
        trialPropertyNames.put("diseaseName","disease_name");
   		trialPropertyNames.put("diseaseSubCategory","disease_sub_category");
   		trialPropertyNames.put("stage","Stage");
   		trialPropertyNames.put("grade","Grade");
		trialPropertyNames.put("histoparhology","Histopathology");
		trialPropertyNames.put("studyType","study_type (Clinical/PreClinical)");
		trialPropertyNames.put("cellLineName","Cell line/ Model Name");
		trialPropertyNames.put("totalSampleNumber","total_sample_number");
		trialPropertyNames.put("patientNumberInCase","patient_number (case)");
		trialPropertyNames.put("patientNumberInReference","patient_number (reference)");
		trialPropertyNames.put("age","age (case)");
		trialPropertyNames.put("gender","gender (case)");
		trialPropertyNames.put("ethnicity","ethnicity (case)");
		trialPropertyNames.put("trialStatus","trial_status");
		trialPropertyNames.put("sponsor","sponsor & collaborator");
		trialPropertyNames.put("phase","phase");
		trialPropertyNames.put("inclusionCriteria","inclusion_criteria");
		trialPropertyNames.put("exclusionCriteria","exclusion_criteria");
		trialPropertyNames.put("allocation","allocation");
		trialPropertyNames.put("interventionModel","intervention_model");
		trialPropertyNames.put("masking","masking");
		trialPropertyNames.put("primaryPurpose","primary_purpose");
		
		// markerPropertyNames
		markerPropertyNames.put("identifier","s_no");
		markerPropertyNames.put("name","biomarker_name_STD");
		markerPropertyNames.put("type","marker_type");
		markerPropertyNames.put("nature","marker_nature");
		markerPropertyNames.put("typeOfVariation","type_of_variation");
		markerPropertyNames.put("HGVSName","HGVS Name");
		markerPropertyNames.put("association","association");
		markerPropertyNames.put("markerAlteration","marker_alteration");
		markerPropertyNames.put("typeOfAlteration","type of alteration");
		markerPropertyNames.put("phenotype","phenotype");
		markerPropertyNames.put("phenotypeAlteration","phenotype_alteration");
		markerPropertyNames.put("significance","significance");
		markerPropertyNames.put("pValue","p_value");
		markerPropertyNames.put("application","application");
    }
    
    private static Pattern chemblIdPat = Pattern.compile("CHEMBL\\d+");

	private static String[] getChemblIds(String chemblIds) {
		if (chemblIds == null) {
			return new String[0];
		}
		ArrayList<String> ids = new ArrayList<>();
		Matcher matcher = chemblIdPat.matcher(chemblIds);
		while (matcher.find()) {
			ids.add(matcher.group());
		}
		return ids.toArray(new String[ids.size()]);
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

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
		if (resolver == null) {
			resolver = new UMLSResolver(mrConsoFile, mrStyFile);
		}
		try(CSVParser parser = new CSVParser(reader, true)){
			String prevReferenceId = null;
			Item item = null;
			for (Map<String, String> map : parser) {
				String referenceId = map.get("reference_id");
				if(prevReferenceId == null || !prevReferenceId.equals(referenceId)) {
					item = createItem("IPFTrial");
					item.setAttribute("name", "IPF:"+referenceId);
					String refType = map.get("reference_type");
					
					if ("PubMed".equals(refType)) {
						item.setReference("reference", getPublication(referenceId));
					}
					
					String trialId = map.get("associated_clinical trials");
					if(isNotNull(trialId)) {
						item.setReference("trialGroup",getTrialGroup(trialId));
					}
					
					for (Entry<String, String> entry : trialPropertyNames.entrySet()) {
						String value = map.get(entry.getValue());
						if(isNotNull(value)) {
							item.setAttribute(entry.getKey(), value);
						}
					}
					
					String diseaseName = map.get("disease_name");
					if(diseaseName!=null){
						String cui = resolver.getIdentifier(map.get("disease_name"));
						if(!StringUtils.isEmpty(cui)) {
							item.setReference("diseaseUmls", getUMLSTerm(cui));
						}
					}

					String chemblIds = map.get("ChEMBL");
			    	for (String chemblId : getChemblIds(chemblIds)) {
			    		if(!StringUtils.isEmpty(chemblId)) {
			    			item.addToCollection("chemblCompounds", getChemblCompound(chemblId));
			    		}
					}
					store(item);
				}
				
				prevReferenceId = referenceId;
				Item biomarkerItem = createItem("IPFBiomarker");
				biomarkerItem.setReference("trial", item);
				for (Entry<String, String> entry : markerPropertyNames.entrySet()) {
					String value = map.get(entry.getValue());
					if(!Utils.isEmpty(value) && !"[NA]".equals(value)) {
						biomarkerItem.setAttribute(entry.getKey(), value);
					}
				}
				String entrezIdString = map.get("Entrez id");
				if (isNotNull(entrezIdString)) {
					String[] entrezIds = entrezIdString.split("\\||;\\s|,\\s|\\s");
					for (String geneId: entrezIds) {
						if (isNotNull(geneId)) {
							biomarkerItem.addToCollection("genes", getGene(geneId));
						}
					}
				}
				
				String uniprotIdString = map.get("Uniprot id");
				if (isNotNull(uniprotIdString)) {
					String[] uniprotIds = uniprotIdString.split("\\||;\\s|,\\s|\\s");
					for (String accession: uniprotIds) {
						if (isNotNull(accession)) {
							biomarkerItem.addToCollection("proteins", getProtein(accession));
						}
					}
				}
				
				store(biomarkerItem);
				
			}

		}

    }

	private Map<String, String> geneMap = new HashMap<String, String>();

	private String getGene(String geneId) throws ObjectStoreException {
		String ret = geneMap.get(geneId);
		if (ret == null) {
			Item item = createItem("Gene");
			item.setAttribute("primaryIdentifier", geneId);
			ret = item.getIdentifier();
			store(item);
			geneMap.put(geneId, ret);
		}
		return ret;
	}
	private Map<String, String> publicationMap = new HashMap<String, String>();

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

	private Map<String, String> ontologyItemMap = new HashMap<String, String>();

	private String getUMLSTerm(String identifier) throws ObjectStoreException {
		String ret = ontologyItemMap.get(identifier);
		if (ret == null) {
			Item item = createItem("UMLSTerm");
			item.setAttribute("identifier", identifier);
			store(item);
			ret = item.getIdentifier();
			ontologyItemMap.put(identifier, ret);
		}
		return ret;
	}

	private Map<String, String> proteinMap = new HashMap<String, String>();

	private String getProtein(String primaryAccession) throws ObjectStoreException {
		String ret = proteinMap.get(primaryAccession);
		if (ret == null) {
			Item item = createItem("Protein");
			item.setAttribute("primaryAccession", primaryAccession);
			store(item);
			ret = item.getIdentifier();
			proteinMap.put(primaryAccession, ret);
		}
		return ret;
	}

	private Map<String, String> trialGroupMap = new HashMap<String, String>();
	
	private String getTrialGroup(String identifier) throws ObjectStoreException {
		String ret = trialGroupMap.get(identifier);
		if (ret == null) {
			Item item = createItem("TrialGroup");
			item.setAttribute("identifier", identifier);
			store(item);
			ret = item.getIdentifier();
			trialGroupMap.put(identifier, ret);
		}
		return ret;
	}

	private Map<String, String> compoundMap = new HashMap<String, String>();

	private String getChemblCompound(String chemblId) throws ObjectStoreException {
		String ret = compoundMap.get(chemblId);
		if (ret == null) {
			Item item = createItem("ChemblCompound");
			item.setAttribute("originalId", chemblId);
			store(item);
			ret = item.getIdentifier();
			compoundMap.put(chemblId, ret);
		}
		return ret;
	}
	
	private static boolean isNotNull(String value) {
		return !(StringUtils.isEmpty(value) || value.equals("[NA]"));
	}
}
