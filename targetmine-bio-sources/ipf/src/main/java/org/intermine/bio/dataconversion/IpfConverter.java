package org.intermine.bio.dataconversion;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

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
public class IpfConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "IPF";
    private static final String DATA_SOURCE_NAME = "IPF";

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
	public IpfConverter(ItemWriter writer, Model model) {
		super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
	}

	private static Map<String, String> propertyNames = new HashMap<String, String>();
    
	static {
		propertyNames.put("identifier", "S.No");
		// From Node
		propertyNames.put("fromNodeName", "from_node_name_STD");
		propertyNames.put("fromNodeType", "from_node_type");
		propertyNames.put("fromNodeNature", "from_node_nature");
		propertyNames.put("fromNodeAnalysis", "from_node_analysis");
		propertyNames.put("fromNodeAlteration", "from_node_alteration_stated");
		propertyNames.put("fromNodeEffect", "from_node_Effect");
		// To Node
		propertyNames.put("toNodeName", "to_node_name_STD");
		propertyNames.put("toNodeType", "to_node_type");
		propertyNames.put("toNodeNature", "to_node_nature");
		propertyNames.put("toNodeAnalysis", "to_node_analysis_stated");
		propertyNames.put("toNodeAlteration", "to_node_alteration_stated");
		// Phenotype Details
		propertyNames.put("phenotype", "phenotype_STD");
		propertyNames.put("go", "GO_ontology_ID");
		propertyNames.put("phenotypeAlteration", "phenotype_alteration");
		propertyNames.put("significance", "significance");
		propertyNames.put("relationship", "relationship_type");
		propertyNames.put("annotation", "disease Annotation");
		propertyNames.put("therapy", "drug/therapy (Seperator '|'; '+')");
		propertyNames.put("referenceDrug", "reference_drug/therapy (Seperator '|'; '+')");
		propertyNames.put("treatmentDetail", "treatment_details (Seperator '\\\\')");
		propertyNames.put("chemblCompound", "Chembl_ID");
		propertyNames.put("mechanismOfAction", "compound_mechanism_of_action");
		propertyNames.put("inducer", "inducer_details");
		propertyNames.put("inhibitor", "Inhibitor");
		propertyNames.put("studyType", "study_type (Clinical/Preclinical-In Vitro/Preclinical-In Vivo)");
		propertyNames.put("methodology", "methodology/technique (From and To node \"\"|\"\" and multiple ';')");
		propertyNames.put("specimen", "specimen");
		propertyNames.put("model_name", "cells /cell_line/ model_name");
		propertyNames.put("preclinical", "preclinical_details");
	}
    
    /**
     * 
     *
     * {@inheritDoc}
     */
	public void process(Reader reader) throws Exception {
		try (CSVParser parser = new CSVParser(reader, true)) {
			for (Map<String, String> map : parser) {
				Item item = createItem("IPF");
				String pubmedId = map.get("PubMed id");
				if (!StringUtils.isEmpty(pubmedId)) {
					item.setReference("reference", getPublication(pubmedId));
				}

				String umlsId = map.get("UMLS code");
				if (!StringUtils.isEmpty(umlsId)) {
					item.setReference("diseaseUmls", getUMLSTerm(umlsId));
				}

				String fromEntrezGeneId = map.get("from_node_Entrez id");
				if (isNotNull(fromEntrezGeneId)) {
					for (String id : fromEntrezGeneId.split("\\||;\\s|,\\s|\\s")) {
						if (isNotNull(id)) {
							item.addToCollection("fromNodeGenes", getGene(id));
						}
					}
				}

				String toEntrezGeneId = map.get("to_node_Entrez ID");
				if (isNotNull(toEntrezGeneId)) {
					for (String id : toEntrezGeneId.split("\\||;\\s|,\\s|\\s")) {
						if (isNotNull(id)) {
							item.addToCollection("toNodeGenes", getGene(id));
						}
					}
				}

				String fromUniportIds = map.get("from_node_Uniprot id");
				if (isNotNull(fromUniportIds)) {
					for (String id : fromUniportIds.split("\\||;\\s|,\\s|\\s")) {
						if (isNotNull(id)) {
							item.addToCollection("fromNodeProteins", getProtein(id));
						}
					}
				}

				String toUniportIds = map.get("to_node_Uniprot id");
				if (isNotNull(toUniportIds)) {
					for (String id : toUniportIds.split("\\||;\\s|,\\s|\\s")) {
						if (isNotNull(id)) {
							item.addToCollection("toNodeProteins", getProtein(id));
						}
					}
				}

				for (Entry<String, String> entry : propertyNames.entrySet()) {
					String value = map.get(entry.getValue());
					if (isNotNull(value)) {
						item.setAttribute(entry.getKey(), value);
					}
				}
				store(item);
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

	private static boolean isNotNull(String value) {
		return !(StringUtils.isEmpty(value) || value.equals("[NA]"));
	}
}
