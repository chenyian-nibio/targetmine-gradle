package org.intermine.bio.dataconversion;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.sql.Database;
import org.intermine.xml.full.Item;

/**
 * Integrate dbsnp in advance. Read hgmd database in mysql. mysql db setting
 * wrote '~/.intermine/targetmine.properties'.
 *
 * @author mss-uehara-san (create)
 * @author chenyian (refine)
 */
public class HgmdConverter extends BioDBConverter {
	private static final Logger LOG = LogManager.getLogger(HgmdConverter.class);
	//
	private static final String DATASET_TITLE = "hgmd";
	private static final String DATA_SOURCE_NAME = "hgmd";

	private Map<String, String> geneMap = new HashMap<String, String>();
	private Map<String, String> snpMap = new HashMap<String, String>();
	private Map<String, String> umlsTermMap = new HashMap<String, String>();
	private Map<String, String> publicationMap = new HashMap<String, String>();
	private Map<String, String> variationAnnotationMap = new HashMap<String, String>();

	/**
	 * Construct a new HgmdConverter.
	 *
	 * @param database the database to read from
	 * @param model    the Model used by the object store we will write to with the
	 *                 ItemWriter
	 * @param writer   an ItemWriter used to handle Items created
	 */
	public HgmdConverter(Database database, Model model, ItemWriter writer) {
		super(database, model, writer, DATA_SOURCE_NAME, DATASET_TITLE);
	}

	@Override
	public String getLicence() {
		return "http://www.hgmd.cf.ac.uk/docs/register.html";
	}

	/**
	 * {@inheritDoc}
	 */
	public void process() throws Exception {
		// a database has been initialised from properties starting with db.hgmd
		Connection connection = getDatabase().getConnection();
		Statement stmt = connection.createStatement();
		
		String queryCui = "SELECT hgmd_pro.allmut.acc_num AS acc_num, " 
				+ "hgmd_phenbase.phenotype_concept.cui AS cui " + "FROM hgmd_pro.allmut "  
				+ "JOIN hgmd_phenbase.hgmd_mutation ON "
				+ "hgmd_pro.allmut.acc_num = hgmd_phenbase.hgmd_mutation.acc_num "
				+ "JOIN hgmd_phenbase.phenotype_concept ON "
				+ "hgmd_phenbase.hgmd_mutation.phen_id = hgmd_phenbase.phenotype_concept.phen_id " + ";";
		ResultSet resCui = stmt.executeQuery(queryCui);
		Map<String, Set<String>> hgmdsCuiMap = new HashMap<String, Set<String>>();
		while (resCui.next()) {
			String cui = "UMLS:" + resCui.getString("cui");
			String acc = resCui.getString("acc_num");
			if (hgmdsCuiMap.get(acc) == null) {
				hgmdsCuiMap.put(acc, new HashSet<String>());
			}
			hgmdsCuiMap.get(acc).add(cui);
		}

//		String queryAllmut = "SELECT * from hgmd_pro.allmut " + "LEFT JOIN hgmd_pro.mutnomen ON "
//				+ "hgmd_pro.allmut.acc_num = hgmd_pro.mutnomen.acc_num " + ";";
		String queryAllmut = " SELECT a1.*, a2.entrezID " + 
				" FROM hgmd_pro.allmut a1 " + 
				" LEFT JOIN hgmd_pro.allgenes a2 ON a1.gene = a2.gene";

		ResultSet resAllmut = stmt.executeQuery(queryAllmut);
		while (resAllmut.next()) {
			// hgmd data input. return hgmd id.
			String identifier = resAllmut.getString("acc_num");
			String description = resAllmut.getString("descr");
			String variantClass = resAllmut.getString("tag");
			String mutype = resAllmut.getString("mutype");
			String pubMedId = resAllmut.getString("pmid");

			Item hgmdItem = createItem("Hgmd");
			hgmdItem.setAttribute("identifier", identifier);
			hgmdItem.setAttribute("description", description);
			hgmdItem.setAttribute("variantClass", variantClass);
			hgmdItem.setAttribute("mutantType", mutype);
			if (!StringUtils.isEmpty(pubMedId)) {
				hgmdItem.setReference("publication", getPublication(pubMedId));
			}

			// snp data input & reference hgmd. return snpId.
			// hgmd にdbsnpがあればそのまま使用、なければacc_numで代用
			String snpId = resAllmut.getString("dbsnp");
			if (StringUtils.isEmpty(snpId)) {
				snpId = identifier;
			}
			
			String snpRef = snpMap.get(snpId);

			if (snpRef == null) {
				Item item;
				// hgmd にdbsnpのIDが入っていればSNP にリンクを張る。
				if (snpId.startsWith("rs")) {
					item = createItem("SNP");
					item.setAttribute("identifier", snpId);
				} else {
					// 無い場合はhgmdのデータを利用してSNPの情報を埋める
					item = createItem("Variant");
					item.setAttribute("identifier", snpId);

					// TODO: データの作り方 要確認 : allmut.hgvsまたはallmut.deletionまたはallmut.insertion
					String refSnpAllele = "";
					if (!StringUtils.isEmpty(resAllmut.getString("hgvs"))) {
						refSnpAllele = convertHGVSTorefSnpAllele(resAllmut.getString("hgvs"), identifier);
					} else if (!StringUtils.isEmpty(resAllmut.getString("deletion"))) {
						refSnpAllele = resAllmut.getString("deletion");
					} else if (!StringUtils.isEmpty(resAllmut.getString("insertion"))) {
						refSnpAllele = resAllmut.getString("insertion");
					}
					
					if ("".equals(refSnpAllele)) {
						String descr = resAllmut.getString("descr");
						item.setAttribute("description", descr);
					} else {
						String coodStart = resAllmut.getString("startCoord");
						String chromosome = resAllmut.getString("chromosome");
						if (!StringUtils.isEmpty(coodStart)) {
							String location = combineString(chromosome, coodStart, ":");
							item.setAttribute("location", location);
							item.setAttribute("coordinate", coodStart);
						}
						if (!StringUtils.isEmpty(chromosome)) {
							item.setAttribute("chromosome", chromosome);
						}
						if (!StringUtils.isEmpty(refSnpAllele)) {
							item.setAttribute("description", refSnpAllele);
						}
					}
				}
				store(item);
				snpRef = item.getIdentifier();
				snpMap.put(snpId, snpRef);
				
				String geneId = resAllmut.getString("entrezID");
				if (!StringUtils.isEmpty(geneId)) {
					String variationId = combineString(snpId, geneId, "-");
					// VariationAnnotaition data input & reference geneId, SNPId.
					createVariationAnnotation(variationId, getGene(geneId), snpRef);
				}
			}
			
			Set<String> cuiIds = hgmdsCuiMap.get(identifier);
			if (cuiIds != null) {
				for (String cui : cuiIds) {
					hgmdItem.addToCollection("umlsTerms", getUmlsTerm(cui));
				}
			}
			
			hgmdItem.setReference("snp", snpRef);
			store(hgmdItem);
		}

		stmt.close();
		connection.close();
	}

	private static final Pattern hgvsPattern = Pattern.compile("\\d+(\\w+)>(\\w+)");

	private static String convertHGVSTorefSnpAllele(String hgvs, String id) {
		Matcher matcher = hgvsPattern.matcher(hgvs);
		if (matcher.matches()) {
			return matcher.group(1) + "/" + matcher.group(2);
		} else {
			LOG.warn("Unexpected hgvs " + hgvs + " at " + id);
			return hgvs;
		}
	}

	private String getUmlsTerm(String cui) throws Exception {
		String ret = umlsTermMap.get(cui);
		if (ret == null) {
			Item item = createItem("UMLSTerm");
			item.setAttribute("identifier", cui);
			store(item);
			ret = item.getIdentifier();
			umlsTermMap.put(cui, ret);
		}
		return ret;
	}

	private String getGene(String primaryIdentifier) throws ObjectStoreException {
		if (StringUtils.isEmpty(primaryIdentifier)) {
			return "";
		}
		String ret = geneMap.get(primaryIdentifier);

		if (ret == null) {
			Item item = createItem("Gene");
			item.setAttribute("primaryIdentifier", primaryIdentifier);
			item.setAttribute("ncbiGeneId", primaryIdentifier);
			store(item);
			ret = item.getIdentifier();
			geneMap.put(primaryIdentifier, ret);
		}
		return ret;
	}

	private String createVariationAnnotation(String variationId, String geneRef, String snpRef)
			throws ObjectStoreException {
		String ret = variationAnnotationMap.get(variationId);
		if (ret == null) {
			Item item = createItem("VariationAnnotation");
			item.setAttribute("identifier", variationId);
			if (!StringUtils.isEmpty(geneRef)) {
				item.setReference("gene", geneRef);
			}
			if (!StringUtils.isEmpty(snpRef)) {
				item.setReference("snp", snpRef);
			}
			store(item);
			ret = item.getIdentifier();
			variationAnnotationMap.put(variationId, ret);
		}
		return ret;
	}

	private String combineString(String str1, String str2, String combineStr) {
		String str = "";
		if (!StringUtils.isEmpty(str1) && !StringUtils.isEmpty(str2)) {
			str = str1 + combineStr + str2;
		} else if (!StringUtils.isEmpty(str1) && StringUtils.isEmpty(str2)) {
			str = str1;
		} else if (StringUtils.isEmpty(str1) && !StringUtils.isEmpty(str2)) {
			str = str2;
		}

		return str;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getDataSetTitle(String taxonId) {
		return DATASET_TITLE;
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

}
