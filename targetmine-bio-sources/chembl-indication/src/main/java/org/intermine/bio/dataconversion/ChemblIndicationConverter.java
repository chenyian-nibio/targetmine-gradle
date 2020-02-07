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
public class ChemblIndicationConverter extends BioDBConverter {
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
	public ChemblIndicationConverter(Database database, Model model, ItemWriter writer) {
		super(database, model, writer, DATA_SOURCE_NAME, DATASET_TITLE);
	}

	/**
	 * {@inheritDoc}
	 */
	public void process() throws Exception {
		Connection connection = getDatabase().getConnection();

		Statement stmt = connection.createStatement();
		String queryRef = " SELECT drugind_id, ref_type, ref_id, ref_url FROM indication_refs WHERE ref_id IS NOT null ";
		ResultSet resRef = stmt.executeQuery(queryRef);
		Map<Integer,Set<RefEntry>> refMap = new HashMap<Integer, Set<RefEntry>>();
		while (resRef.next()) {
			int indId = resRef.getInt("drugind_id");
			String source = resRef.getString("ref_type");
			String refId = resRef.getString("ref_id").trim();
			String refUrl = resRef.getString("ref_url").trim();
			if (refMap.get(Integer.valueOf(indId)) == null) {
				refMap.put(Integer.valueOf(indId), new HashSet<RefEntry>());
			}
			refMap.get(Integer.valueOf(indId)).add(new RefEntry(source, refId, refUrl));
		}

		String queryIndication = " SELECT di.drugind_id AS drugind_id, di.max_phase_for_ind AS phase, "
				+ " di.mesh_id AS mesh_id, di.mesh_heading AS mesh_heading, "
				+ " di.efo_id AS efo_id, md.chembl_id AS chembl_id "
				+ " FROM drug_indication AS di "
				+ " JOIN molecule_dictionary AS md ON md.molregno = di.molregno  ";
		ResultSet resIndication = stmt.executeQuery(queryIndication);
		while (resIndication.next()) {
			String chemblId = resIndication.getString("chembl_id");
			String meshId = resIndication.getString("mesh_id");
			String meshHeading = resIndication.getString("mesh_heading");
			String efoId = resIndication.getString("efo_id");
			int phase = resIndication.getInt("phase");
			int indId = resIndication.getInt("drugind_id");
			
			Item item = createItem("Indication");
			item.setAttribute("maxPhase", String.valueOf(phase));
			item.setAttribute("title", meshHeading);
			item.setReference("compound", getChemblCompound(chemblId));
			item.addToCollection("ontologyTerms", getMeshTerm(meshId));
			if (efoId != null) {
				item.addToCollection("ontologyTerms", getEFOTerm(efoId));
			}
			if (refMap.get(Integer.valueOf(indId)) != null) {
				for (RefEntry re : refMap.get(Integer.valueOf(indId))) {
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
	
	private Map<String, String> ontologyTermMap = new HashMap<String, String>();
	
	private String getMeshTerm(String meshId) throws ObjectStoreException {
		String ret = ontologyTermMap.get(meshId);
		if (ret == null) {
			Item item = createItem("MeshTerm");
			item.setAttribute("identifier", meshId);
			store(item);
			ret = item.getIdentifier();
			ontologyTermMap.put(meshId, ret);
		}
		return ret;
	}

	private String getEFOTerm(String efoId) throws ObjectStoreException {
		String ret = ontologyTermMap.get(efoId);
		if (ret == null) {
			Item item = createItem("EFOTerm");
			item.setAttribute("identifier", efoId);
			store(item);
			ret = item.getIdentifier();
			ontologyTermMap.put(efoId, ret);
		}
		return ret;
	}

	private Map<String, String> compoundMap = new HashMap<String, String>();

	private String getChemblCompound(String chemblId) throws ObjectStoreException {
		String ret = compoundMap.get(chemblId);
		if (ret == null) {
			Item item = createItem("ChemblCompound");
			item.setAttribute("originalId", chemblId);
//			item.setAttribute("identifier", String.format("ChEMBL:%s", chemblId));
			store(item);
			ret = item.getIdentifier();
			compoundMap.put(chemblId, ret);
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
			item.setAttribute("url", url);
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
