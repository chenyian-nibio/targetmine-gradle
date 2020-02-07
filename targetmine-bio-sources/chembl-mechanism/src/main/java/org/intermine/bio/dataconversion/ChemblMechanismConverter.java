package org.intermine.bio.dataconversion;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.sql.Database;
import org.intermine.xml.full.Item;

/**
 * 
 * @author chenyian
 */
public class ChemblMechanismConverter extends BioDBConverter {
	//
	private static final String DATASET_TITLE = "ChEMBL";
	private static final String DATA_SOURCE_NAME = "ChEMBL";

	/**
	 * Construct a new ChemblIndicationConverter.
	 * 
	 * @param database the database to read from
	 * @param model the Model used by the object store we will write to with the ItemWriter
	 * @param writer an ItemWriter used to handle Items created
	 */
	public ChemblMechanismConverter(Database database, Model model, ItemWriter writer) {
		super(database, model, writer, DATA_SOURCE_NAME, DATASET_TITLE);
	}

	/**
	 * {@inheritDoc}
	 */
	public void process() throws Exception {
		Connection connection = getDatabase().getConnection();

		Statement stmt = connection.createStatement();
		String queryRef = " SELECT mec_id, ref_type, ref_id, ref_url FROM mechanism_refs WHERE ref_id IS NOT null ";
		ResultSet resRef = stmt.executeQuery(queryRef);
		Map<Integer,Set<RefEntry>> refMap = new HashMap<Integer, Set<RefEntry>>();
		while (resRef.next()) {
			int mecId = resRef.getInt("mec_id");
			String source = resRef.getString("ref_type");
			String refId = resRef.getString("ref_id").trim();
			String refUrl = resRef.getString("ref_url");
			if (refMap.get(Integer.valueOf(mecId)) == null) {
				refMap.put(Integer.valueOf(mecId), new HashSet<RefEntry>());
			}
			refMap.get(Integer.valueOf(mecId)).add(new RefEntry(source, refId, refUrl));
		}
		
		String queryMechanism = " SELECT md.chembl_id AS chembl_id, dm.mechanism_of_action AS mechanism_of_action, "
				+ " dm.action_type AS action_type, dm.mec_id AS mec_id, "
				+ " pc.pref_name AS target_class, cs.accession AS accession " 
				+ " FROM molecule_dictionary AS md "
				+ " JOIN drug_mechanism AS dm ON md.molregno = dm.molregno "
				+ " JOIN target_components AS tc ON dm.tid = tc.tid "
				+ " JOIN component_sequences AS cs ON tc.component_id = cs.component_id "
				+ " JOIN component_class AS cc ON cc.component_id = cs.component_id "
				+ " JOIN protein_classification AS pc ON pc.protein_class_id = cc.protein_class_id "
				+ " WHERE cs.accession IS NOT null ";
		
		ResultSet resMechanism = stmt.executeQuery(queryMechanism);
		while (resMechanism.next()) {
			String chemblId = resMechanism.getString("chembl_id");
			String proteinAcc = resMechanism.getString("accession");
			String action = resMechanism.getString("mechanism_of_action");
			String actionType = resMechanism.getString("action_type");
			String targetClass = resMechanism.getString("target_class");
			int mecId = resMechanism.getInt("mec_id");
			
			Item item = createItem("DrugMechanism");
			item.setAttribute("action", action);
			if (actionType != null) {
				item.setAttribute("actionType", actionType.toLowerCase());
			}
			item.setAttribute("targetClass", targetClass);
			item.setReference("compound", getChemblCompound(chemblId));
			item.setReference("target", getProtein(proteinAcc));
			if (refMap.get(Integer.valueOf(mecId)) != null) {
				for (RefEntry re : refMap.get(Integer.valueOf(mecId))) {
					item.addToCollection("references", getReference(re.getSource(), re.getId(), re.getUrl()));;
				}
			}
			store(item);
		}

		stmt.close();
		connection.close();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getDataSetTitle(String taxonId) {
		return DATASET_TITLE;
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

	private Map<String, String> referenceMap = new HashMap<String, String>();
	
	private String getReference(String source, String identifier, String url) throws ObjectStoreException {
		String key = String.format("%s-%s", source, identifier);
		String ret = referenceMap.get(key);
		if (ret == null) {
			Item item = createItem("Reference");
			item.setAttribute("source", source);
			item.setAttribute("identifier", identifier);
			if (url != null) {
				item.setAttribute("url", url.trim());
			}
			if (source.equals("PubMed")) {
				item.setReference("publication", getPublication(identifier));
			}
			store(item);
			ret = item.getIdentifier();
			referenceMap.put(key, ret);
		}
		return ret;
	}

	private Map<String, String> publicationMap = new HashMap<String, String>();

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

	private static class RefEntry {
		private String source;
		private String id;
		private String url;
		public RefEntry(String source, String id, String url) {
			super();
			this.source = source;
			this.id = id;
			this.url = url;
		}
		String getSource() {
			return source;
		}
		String getId() {
			return id;
		}
		String getUrl() {
			return url;
		}
	}

	@Override
	public String getLicence() {
		return "https://creativecommons.org/licenses/by-sa/3.0/";
	}
}
