package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2019 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;


/**
 * 
 * @author
 */
public class IpfConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "Add DataSet.title here";
    private static final String DATA_SOURCE_NAME = "Add DataSource.name here";

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public IpfConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }
    private String osAlias = null;
    public void setOsAlias(String osAlias) {
        this.osAlias = osAlias;
    }

    private static Map<String, String> propertyNames = new HashMap<String, String>();
    
    static {
        Map<String, String> p = new HashMap<>();
        p.put("referenceType","reference_type");
        // reference_id
        //associated_clinical trials
  		p.put("therapy","drug/therapy");
  		p.put("referenceTherapy","reference_drug/therapy");
  		p.put("treatmentDetails","treatment_details (Seperator '\\')");
  		p.put("dose","dose");
  		p.put("routeOfAdministration","route of administration");
  		p.put("duration","duration");
  		//CAS id
        //ChEMBL
        //drug bank id
  		p.put("approvedDrug","approved_drug");
        p.put("approvalAutority","approval_authority");
        p.put("diseaseName","disease_name");
   		p.put("diseaseSubCategory","disease_sub_category");
   		p.put("stage","Stage");
   		p.put("grade","Grade");
		p.put("histoparhology","Histopathology");
        //biomarker_name_as_mentioned_in_reference

		p.put("studyType","study_type (Clinical/PreClinical)");
		p.put("cellLineName","Cell line/ Model Name");
		p.put("totalSampleNumber","total_sample_number");
		p.put("patientNumberInCase","patient_number (case)");
		p.put("patientNumberInReference","patient_number (reference)");
		p.put("age","age (case)");
		p.put("gender","gender (case)");
		p.put("ethnicity","ethnicity (case)");
		p.put("trialStatus","trial_status");
		p.put("sponsor","sponsor & collaborator");
		p.put("phase","phase");
		p.put("inclusionCriteria","inclusion_criteria");
		p.put("exclusionCriteria","exclusion_criteria");
		p.put("allocation","allocation");
		p.put("interventionModel","intervention_model");
		p.put("masking","masking");
		p.put("primaryPurpose","primary_purpose");
		trialPropertyNames = p;
		
		
		
        p = new HashMap<>();
		p.put("biomarkerName","biomarker_name_STD");
		p.put("markerType","marker_type");
		p.put("markerNature","marker_nature");
        	p.put("identifier","s_no");
        //Entrez id
        //Uniprot id
		p.put("typeOfVariation","type_of_variation");
		//"rs_id"
		p.put("HGVSName","HGVS Name");
		p.put("association","association");
		p.put("markerAlteration","marker_alteration");
		p.put("typeOfAlteration","type of alteration");
		p.put("phenotype","phenotype");
		p.put("phenotypeAlteration","phenotype_alteration");
		p.put("significance","significance");
		p.put("relationship","relationship_type");
		p.put("annotation","disease Annotation");
        //KEGG pathway ID #NODATA
		p.put("therapy","drug/therapy (Seperator '|'; '+')");
		p.put("referenceDrug","reference_drug/therapy (Seperator '|'; '+')");
		p.put("treatmentDetail","treatment_details (Seperator '\\')");
		p.put("chemblCompound","Chembl_ID");
		p.put("mechanismOfAction","compound_mechanism_of_action");
		//"compound_mechanism_of_action_ID
        //target_name
        //target_chembl_id
		p.put("inducer","inducer_details");
		p.put("inhibitor","Inhibitor");
		p.put("studyType","study_type (Clinical/Preclinical-In Vitro/Preclinical-In Vivo)");
		p.put("methodology","methodology/technique (From and To node \"|\" and multiple proteins';' and '/' for multiple methods)");
		p.put("specimen","specimen");
		p.put("model_name","cells /cell_line/ model_name");
		p.put("preclinical","preclinical_details");
    }
	ItemCreator geneCreator = new ItemCreator(this,"Protein","primaryIdenfitifer");
	DBIDFinder geneIdFinder = new DBIDFinder(osAlias,"Protein","ncbiGeneId","primaryAccession");
    private void addReferenceToGene(Item item,String collectionName,String uniportIds) throws Exception {
    	if(!Utils.empty(uniportIds)) {
    		return;
    	}
    	String[] ids = uniportIds.split("\\|");
    	for (String uniportId : ids) {
    		String identifier = proteinIdFinder.getIdentifierByValue(uniportId);
    		if(!Utils.empty(identifier)) {
    			String proteinRef = proteinCreator.createItemRef(identifier);
    			item.addToCollection(collectionName, proteinRef);
    		}
		}
    }
	ItemCreator proteinCreator = new ItemCreator(this,"Protein","primaryIdenfitifer");
	DBIDFinder proteinIdFinder = new DBIDFinder(osAlias,"Protein","primaryAccession","primaryIdenfitifer");
    private void addReferenceToProtein(Item item,String collectionName,String uniportIds) throws Exception {
    	if(!Utils.empty(uniportIds)) {
    		return;
    	}
    	String[] ids = uniportIds.split("\\|");
    	for (String uniportId : ids) {
    		String identifier = proteinIdFinder.getIdentifierByValue(uniportId);
    		if(!Utils.empty(identifier)) {
    			String proteinRef = proteinCreator.createItemRef(identifier);
    			item.addToCollection(collectionName, proteinRef);
    		}
		}
    }
    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
    	ItemCreator publicationCreator = new ItemCreator(this,"Publication","pubMedId");
    	ItemCreator diseaseCreator = new ItemCreator(this,"DiseaseConcept","identifier");
		try(CSVParser parser = new CSVParser(reader, true)){
			for (Map<String, String> map : parser) {
				String referenceId = map.get("reference_id");
				if(prevReferenceId == null || prevReferenceId.equals(referenceId)) {
					item = createItem("IPFTrial");
					String refType = map.get("reference_type");
					String trialName = referenceId;
					if ("PubMed".equals(refType)) {
						String referenceRef = publicationCreator.createItemRef(referenceId);
						item.setReference("reference", referenceRef);
						trialName = map.get("associated_clinical trials");
					}else {
						
					}
					String trialIdentifier = trialGroupFinder.getIdentifierByValue(trialName);
					if(!Utils.empty(trialIdentifier)) {
						String trialGroupRef = trialGrouopCreator.createItemRef(trialIdentifier);
						item.setReference("trialGroup",trialGroupRef);
					}
					for (Entry<String, String> entry : trialPropertyNames.entrySet()) {
						String value = map.get(entry.getValue());
						if(!Utils.isEmpty(value) || "[NA]".equals(value)) {
							item.setAttribute(entry.getKey(), value);
						}
					}
					store(item);
				}
				prevReferenceId = referenceId;
				
				
			}

		}

    }
}
