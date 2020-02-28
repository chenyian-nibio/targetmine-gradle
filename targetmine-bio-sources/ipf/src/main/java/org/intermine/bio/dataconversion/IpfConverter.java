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
        // key:hgmd_pro.allmut.mutype , value:snpfunction.name
        p.put("Idenfitifer","S.No");
        p.put("reference","PubMed id");
        // disease_name_as_mentioned_in_reference
        		p.put("diseaseName","disease_name_STD");
        // disease_sub_category_stated  #NODATA
        // stage	grade  #NODATA
        // histopathology	
        // ICD10 code
        // ICD11 code
        // MeSH code
        // EFO code
        // MedGen code
   		p.put("umlsId","UMLS code");
        // from_node_name_as_mentioned_in_reference
   		p.put("fromNodeName","from_node_name_STD");
		p.put("fromNodeType","from_node_type");
		p.put("fromNodeNature","from_node_nature");
		// p.put("fromNodeGenes","from_node_Entrez id");
        // from_node_Multiple Loci
		p.put("fromNodeProteins","from_node_Uniprot id");
        //variation_type
        //rs_number	
        //HGVS Name (Nucleotide/Protein)
		p.put("fromNodeAnalysis","from_node_analysis");
		p.put("fromNodeAlteration","from_node_alteration_stated");
		p.put("fromNodeEffect","from_node_Effect");
        //to_node_name_stated
		p.put("toNodeName","to_node_name_STD");
		p.put("toNodeType","to_node_type");
		p.put("toNodeNature","to_node_nature");
		p.put("toNodeGene","to_node_Entrez ID");
        //to_node_Multiple Loci
		p.put("toNodeProtein","to_node_Uniprot id");
		p.put("toNodeAnalysis","to_node_analysis_stated");
		p.put("toNodeAlternaton","to_node_alteration_stated");
        //phenotype_stated
		p.put("phenotype","phenotype_STD");
		p.put("go","GO_ontology_ID");
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
				Item item = createItem("IPF");
				String publicationRef = publicationCreator.createItemRef(map.get("PubMed id"));
				if(!Utils.empty(publicationRef)) {
					item.setReference("reference", publicationRef);
				}
				String diseaseRef = diseaseCreator.createItemRef("disease_name_STD");
				if(diseaseRef!=null) {
					item.setReference("diseaseUmls", diseaseRef);
				}
				String fromEntrezGeneId = map.get("from_node_Entrez id");
				addReferenceToGene(item, "fromNodeGenes", fromEntrezGeneId);
				String toEntrezGeneId = map.get("to_node_Entrez id");
				addReferenceToGene(item, "toNodeGenes", toEntrezGeneId);
				String fromUniportIds = map.get("from_node_Uniprot id");
				addReferenceToProtein(item, "fromNodeProteins", fromUniportIds);
				String toUniportIds = map.get("to_node_Uniprot id");
				addReferenceToProtein(item, "toNodeProteins", toUniportIds);
				for (Entry<String, String> entry : propertyNames.entrySet()) {
					String value = map.get(entry.getValue());
					item.setAttribute(entry.getKey(), value);
					System.out.println("key ="+entry.getKey()+", value=" + entry.getValue());
				}
			}

		}

    }
}
