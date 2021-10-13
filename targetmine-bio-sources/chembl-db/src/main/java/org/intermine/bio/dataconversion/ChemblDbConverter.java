package org.intermine.bio.dataconversion;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.sql.Database;
import org.intermine.xml.full.Item;

/**
 * 
 * @author chenyian
 */
public class ChemblDbConverter extends BioDBConverter {
	private static final Logger LOG = LogManager.getLogger(ChemblDbConverter.class);
	//
	private static final String DATASET_TITLE = "ChEMBL";
	private static final String DATA_SOURCE_NAME = "ChEMBL";

	private Map<String, Set<String>> drugMap = new HashMap<String, Set<String>>();
	private Map<String, Set<String>> synonymMap = new HashMap<String, Set<String>>();
	private Map<String, Set<String>> atcClassificationMap = new HashMap<String, Set<String>>();

	private Map<String, String> proteinMap = new HashMap<String, String>();
	private Map<String, String> compoundMap = new HashMap<String, String>();
	private Map<String, String> publicationMap = new HashMap<String, String>();
	private Map<String, String> compoundGroupMap = new HashMap<String, String>();
	private Map<String, String> drugTypeMap = new HashMap<String, String>();
	private Map<String, String> interactionMap = new HashMap<String, String>();

	/**
	 * Construct a new ChemblDbConverter.
	 * 
	 * @param database the database to read from
	 * @param model the Model used by the object store we will write to with the ItemWriter
	 * @param writer an ItemWriter used to handle Items created
	 */
	public ChemblDbConverter(Database database, Model model, ItemWriter writer) {
		super(database, model, writer, DATA_SOURCE_NAME, DATASET_TITLE);
	}

	/**
	 * {@inheritDoc}
	 */
	public void process() throws Exception {

		Connection connection = getDatabase().getConnection();

		Statement stmt = connection.createStatement();
		String queryName = "select distinct cr.molregno, compound_name "
				+ " from compound_records as cr " + " where compound_name is not null ";
		ResultSet resName = stmt.executeQuery(queryName);
		while (resName.next()) {
			String molId = String.valueOf(resName.getInt("molregno"));
			String name = resName.getString("compound_name");
			// also save these names as synonyms
			if (synonymMap.get(molId) == null) {
				synonymMap.put(molId, new HashSet<String>());
			}
			synonymMap.get(molId).add(name);
		}

		String queryDrug = "select distinct molregno, trade_name " + " from formulations as fo "
				+ " join products on fo.product_id=products.product_id "
				+ " where approval_date is not null ";
		ResultSet resDrug = stmt.executeQuery(queryDrug);
		while (resDrug.next()) {
			String molId = String.valueOf(resDrug.getInt("molregno"));
			String tradeName = resDrug.getString("trade_name");
			if (drugMap.get(molId) == null) {
				drugMap.put(molId, new HashSet<String>());
			}
			drugMap.get(molId).add(tradeName);
		}
		String querySynonym = "select distinct molregno, synonyms "
				+ " from molecule_synonyms where syn_type != 'RESEARCH_CODE' ";
		ResultSet resSynonym = stmt.executeQuery(querySynonym);
		while (resSynonym.next()) {
			String molId = String.valueOf(resSynonym.getInt("molregno"));
			String synonym = resSynonym.getString("synonyms");
			if (synonymMap.get(molId) == null) {
				synonymMap.put(molId, new HashSet<String>());
			}
			synonymMap.get(molId).add(synonym);
		}
		
		String queryAtcCode = " select molregno, mac.level5 as code, ac.who_name as title "
				+ " from molecule_atc_classification as mac "
				+ " join atc_classification as ac on ac.level5 = mac.level5 ";
		ResultSet resAtcCode = stmt.executeQuery(queryAtcCode);
		while (resAtcCode.next()) {
			String molId = String.valueOf(resAtcCode.getInt("molregno"));
			String code = resAtcCode.getString("code");
			String title = resAtcCode.getString("title");
			if (atcClassificationMap.get(molId) == null) {
				atcClassificationMap.put(molId, new HashSet<String>());
			}
			atcClassificationMap.get(molId).add(String.format("%s::%s", code, title));
		}
		
		// create chembl compound entries for all compound
		String queryMolecule = " select md.molregno, md.pref_name, md.chembl_id, md.molecule_type, "
				+ " md.max_phase, cp.mw_freebase, cp.full_mwt, "
				+ " cs.standard_inchi_key, cs.standard_inchi, canonical_smiles "
				+ " from molecule_dictionary as md "
				+ " left join compound_properties as cp on cp.molregno = md.molregno "
				+ " left join compound_structures as cs on cs.molregno=md.molregno ";
		ResultSet resMolecule = stmt.executeQuery(queryMolecule);
		int c = 0;
		while (resMolecule.next()) {
			String molId = String.valueOf(resMolecule.getInt("molregno"));
			String chemblId = resMolecule.getString("chembl_id");
			String inchiKey = String.valueOf(resMolecule.getString("standard_inchi_key"));
			String inchi = String.valueOf(resMolecule.getString("standard_inchi"));
			String smiles = String.valueOf(resMolecule.getString("canonical_smiles"));
			String moleculeType = resMolecule.getString("molecule_type");
			int maxPhase = resMolecule.getInt("max_phase");
			double mwFreebase = resMolecule.getDouble("mw_freebase");
			double fullMwt = resMolecule.getDouble("full_mwt");
			
			Map<String,String> structureMap = new HashMap<String, String>();
			if (inchi != null && !"".equals(inchi) && !"null".equals(inchi)) {
				structureMap.put("InChI", inchi);
			}
			if (smiles != null && !"".equals(smiles) && !"null".equals(smiles)) {
				structureMap.put("SMILES", smiles);
			}

			String name = String.valueOf(resMolecule.getString("pref_name"));
			if (name == null || name.equals("null")) {
				name = chemblId;
			}
			
			String compoundRef = compoundMap.get(chemblId);
			if (compoundRef == null) {
				Item compound = createItem("ChemblCompound");
				compound.setAttribute("originalId", chemblId);
				compound.setAttribute("identifier", String.format("ChEMBL:%s", chemblId));

				// if the length of the name is greater than 40 characters,
				// use id instead and save the long name as the synonym
				if (name.length() > 40) {
					name = chemblId;
					if (synonymMap.get(molId) == null) {
						synonymMap.put(molId, new HashSet<String>());
					}
				}
				compound.setAttribute("name", name);
				compound.setAttributeIfNotNull("maxPhase", String.valueOf(maxPhase));
				
				compound.setAttributeIfNotNull("molecularWeight", String.valueOf(fullMwt));
				compound.setAttributeIfNotNull("molecularWeightFreebase", String.valueOf(mwFreebase));

				if (!StringUtils.isEmpty(moleculeType) && !moleculeType.equals("Unclassified") && !moleculeType.equals("Unknown")) {
					compound.addToCollection("drugTypes", getDrugType(moleculeType.toLowerCase()));
				}
				
				if (!inchiKey.equals("null")) {
					compound.setAttribute("inchiKey", inchiKey);
					String compoundGroupId = inchiKey.substring(0, inchiKey.indexOf("-"));
					if (compoundGroupId.length() == 14) {
						compound.setReference("compoundGroup",
								getCompoundGroup(compoundGroupId, name));
					} else {
						LOG.error(String.format("Bad InChIKey value: %s, %s .", chemblId, inchiKey));
					}
				}
				
				Set<String> synonyms = synonymMap.get(molId);
				if (synonyms != null) {
					for (String s : synonyms) {
						setSynonyms(compound, s);
					}
				}

				Set<String> tradeNames = drugMap.get(molId);
				if (tradeNames != null) {
					compound.addToCollection("drugTypes", getDrugType("approved"));
					for (String tn : tradeNames) {
						if (!synonyms.contains(tn)) {
							setSynonyms(compound, tn);
						}
					}
				}
				
				Set<String> classifications = atcClassificationMap.get(molId);
				if (classifications != null) {
					for (String atcClassification : classifications) {
						String[] split = atcClassification.split("::");
						compound.addToCollection("atcCodes", getAtcClassification(split[0], split[1]));
					}
				}

				store(compound);
				
				for (String key: structureMap.keySet()) {
					Item structure = createItem("CompoundStructure");
					structure.setAttribute("type", key);
					structure.setAttribute("value", structureMap.get(key));
					structure.setReference("compound", compound);
					store(structure);
				}
				
				compoundRef = compound.getIdentifier();
				compoundMap.put(chemblId, compoundRef);
				c++;
			}
		}
		LOG.info(c + "ChEMBL compounds were stored.");
		
		// query for assay type description
		String queryAssayType = " select assay_type, assay_desc from assay_type ";
		ResultSet resAssayType = stmt.executeQuery(queryAssayType);
		Map<String,String> assayTypeMap = new HashMap<String, String>();
		while (resAssayType.next()) {
			String type = resAssayType.getString("assay_type");
			String desc = resAssayType.getString("assay_desc");
			assayTypeMap.put(type, desc);
		}

		// query all interactions with activities 
		String queryInteraction = " select distinct md.chembl_id, "
				+ " act.standard_type, act.standard_relation, act.standard_value, act.standard_units, "
				+ " cseq.accession, cseq.tax_id, docs.pubmed_id, "
				+ " ass.chembl_id as assay_id, ass.description, ass.assay_type, ass.confidence_score " 
				+ " from activities as act "
				+ " join molecule_dictionary as md on md.molregno=act.molregno "
				+ " join assays as ass on ass.assay_id=act.assay_id "
				+ " join target_dictionary as td on td.tid=ass.tid "
				+ " join target_components as tc on tc.tid=ass.tid "
				+ " join component_sequences as cseq on cseq.component_id=tc.component_id "
				+ " join docs on docs.doc_id=ass.doc_id "
				+ " where ass.confidence_score >= 4 "
				+ " and td.target_type = 'SINGLE PROTEIN' "
				+ " and act.standard_type in ('IC50','Kd','Ki','EC50','AC50') "
				+ " and act.standard_units is not null ";
		ResultSet resInteraction = stmt.executeQuery(queryInteraction);
		int i = 0;
		while (resInteraction.next()) {
			String chemblId = resInteraction.getString("chembl_id");
			String uniprotId = resInteraction.getString("accession");
			String pubmedId = String.valueOf(resInteraction.getInt("pubmed_id"));
			String standardType = resInteraction.getString("standard_type");
			String standardRelation = resInteraction.getString("standard_relation");
			if (standardRelation == null || standardRelation.equals("")) {
				standardRelation = "=";
			}
			float conc = resInteraction.getFloat("standard_value");
			String standardUnit = resInteraction.getString("standard_units");
			String assayId = resInteraction.getString("assay_id");
			String assayDesc = resInteraction.getString("description");
			String assayType = assayTypeMap.get(resInteraction.getString("assay_type"));
			int confidenceScore = resInteraction.getInt("confidence_score");

			String intId = uniprotId + "-" + chemblId;
			String interactionRef = interactionMap.get(intId);
			if (interactionRef == null) {
				String compoundRef = compoundMap.get(chemblId);
				if (compoundRef == null) {
					LOG.info("Unfound ChEMBL compound: " + chemblId);
					continue;
				}

				Item interactionItem = createItem("ChemblInteraction");
				interactionItem.setReference("protein", getProtein(uniprotId));
				interactionItem.setReference("compound", compoundRef);

				store(interactionItem);
				interactionRef = interactionItem.getIdentifier();
				interactionMap.put(intId, interactionRef);
				i++;
			}
			String assayRef = getCompoundProteinInteractionAssay(assayId, assayDesc, assayType,
					Integer.valueOf(confidenceScore), pubmedId);

			Item activity = createItem("Activity");
			activity.setAttribute("type", standardType);
			activity.setAttribute("relation", standardRelation);
			activity.setAttribute("conc", String.valueOf(conc));
			activity.setAttribute("unit", standardUnit);
			activity.setReference("assay", assayRef);
			activity.setReference("interaction", interactionRef);
			store(activity);
		}
		// System.out.println(i + "ChEMBL interaction were integrated.");
		LOG.info(i + "ChEMBL interaction were integrated.");
		
		stmt.close();
		connection.close();
	}

	Map<String, String> assayMap = new HashMap<String, String>();

	private String getCompoundProteinInteractionAssay(String identifier, String name,
			String assayType, Integer confidenceScore, String pubmedId) throws ObjectStoreException {
		String ret = assayMap.get(identifier);
		if (ret == null) {
			Item item = createItem("CompoundProteinInteractionAssay");
			item.setAttribute("identifier", identifier.toLowerCase());
			item.setAttribute("originalId", identifier);
			item.setAttribute("name", name);
			item.setAttribute("assayType", assayType);
			item.setAttribute("confidenceScore", confidenceScore.toString());
			item.setAttribute("source", "ChEMBL");
			// don't create publication which id equals to '0' 
			if (!pubmedId.equals("0")) {
				item.addToCollection("publications", getPublication(pubmedId));
			}
			store(item);
			ret = item.getIdentifier();
			assayMap.put(identifier, ret);
		}
		return ret;
	}

	private String getProtein(String uniprotId) throws ObjectStoreException {
		String ret = proteinMap.get(uniprotId);
		if (ret == null) {
			Item item = createItem("Protein");
			item.setAttribute("primaryAccession", uniprotId);
			store(item);
			ret = item.getIdentifier();
			proteinMap.put(uniprotId, ret);
		}
		return ret;
	}

	private String getPublication(String pubMedId) throws ObjectStoreException {
		String ret = publicationMap.get(pubMedId);
		if (ret == null) {
			Item item = createItem("Publication");
			item.setAttribute("pubMedId", pubMedId);
			store(item);
			ret = item.getIdentifier();
			publicationMap.put(pubMedId, ret);
		}
		return ret;
	}

	private String getDrugType(String name) throws ObjectStoreException {
		String ret = drugTypeMap.get(name);
		if (ret == null) {
			Item item = createItem("DrugType");
			item.setAttribute("name", name);
			store(item);
			ret = item.getIdentifier();
			drugTypeMap.put(name, ret);
		}
		return ret;
	}

	private String getCompoundGroup(String inchiKey, String name) throws ObjectStoreException {
		String ret = compoundGroupMap.get(inchiKey);
		if (ret == null) {
			Item item = createItem("CompoundGroup");
			item.setAttribute("identifier", inchiKey);
			item.setAttribute("name", name);
			store(item);
			ret = item.getIdentifier();
			compoundGroupMap.put(inchiKey, ret);
		}
		return ret;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getDataSetTitle(String taxonId) {
		return DATASET_TITLE;
	}

	private void setSynonyms(Item subject, String value) throws ObjectStoreException {
		Item syn = createItem("CompoundSynonym");
		syn.setAttribute("value", value);
		syn.setReference("subject", subject);
		store(syn);
	}
	
	private Map<String, String> atcMap = new HashMap<String, String>();

	private String getAtcClassification(String atcCode, String name) throws ObjectStoreException {
		String ret = atcMap.get(atcCode);
		if (ret == null) {
			Item item = createItem("AtcClassification");
			item.setAttribute("atcCode", atcCode);
			item.setAttribute("name", name);
			// add parent
			String parentCode = atcCode.substring(0, 5);
			item.setReference("parent", getParent(parentCode));
			
			// create parents; to be improved
			item.addToCollection("allParents", getParent(parentCode));
			item.addToCollection("allParents", getParent(parentCode.substring(0, 4)));
			item.addToCollection("allParents", getParent(parentCode.substring(0, 3)));
			item.addToCollection("allParents", getParent(parentCode.substring(0, 1)));

			store(item);
			ret = item.getIdentifier();
			atcMap.put(atcCode, ret);
		}
		return ret;
	}

	private String getParent(String parentCode) throws ObjectStoreException {
		String ret = atcMap.get(parentCode);
		if (ret == null) {
			Item item = createItem("AtcClassification");
			item.setAttribute("atcCode", parentCode);
			store(item);
			ret = item.getIdentifier();
			atcMap.put(parentCode, ret);
		}
		return ret;
	}

	@Override
	public String getLicence() {
		return "https://creativecommons.org/licenses/by-sa/3.0/";
	}

}
