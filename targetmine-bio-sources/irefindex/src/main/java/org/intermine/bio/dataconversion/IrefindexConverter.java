package org.intermine.bio.dataconversion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.keyvalue.MultiKey;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

/**
 * Parser for PPI data from iRefIndex (https://irefindex.vib.be/wiki/index.php/README_MITAB2.6_for_iRefIndex_17.0);
 * 
 * @author chenyian
 */
public class IrefindexConverter extends BioFileConverter {
	//
	private static final String DATASET_TITLE = "iRefIndex";
	private static final String DATA_SOURCE_NAME = "iRefIndex";
	private static final String TYPE_FILE = "interactiontype.txt";

	private static final Logger LOG = LogManager.getLogger(IrefindexConverter.class);

	private Map<String, String> interactionTypeMap;

	private Map<String, String> geneMap = new HashMap<String, String>();
	private Map<String, String> pubMap = new HashMap<String, String>();
	private Map<String, String> miMap = new HashMap<String, String>();
	private Map<MultiKey, String> expMap = new HashMap<MultiKey, String>();
	private Map<MultiKey, Item> intMap = new HashMap<MultiKey, Item>();

	private Map<String, String> detailMap = new HashMap<String, String>();

	/**
	 * Constructor
	 * 
	 * @param writer
	 *            the ItemWriter used to handle the resultant items
	 * @param model
	 *            the Model
	 */
	public IrefindexConverter(ItemWriter writer, Model model) {
		super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
	}

	/**
	 * 
	 * 
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		if (interactionTypeMap == null) {
			readInteractionType();
		}

		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(new BufferedReader(reader));

		// skip header
		iterator.next();

		while (iterator.hasNext()) {
			String[] cols = iterator.next();

			String sourceDb = getMiDesc(cols[12]);
			if (sourceDb.equals("biogrid")) {
				continue;
			}
			String[] ids = cols[13].split("\\|");
			String sourceId = ids[0];

			Set<String> geneASet = processAltIdentifier(cols[2]);
			if (geneASet.isEmpty()) {
				continue;
			}
			Set<String> geneBSet = processAltIdentifier(cols[3]);
			if (geneBSet.isEmpty()) {
				continue;
			}
			
			// in case there are redundant entries
			String[] pmids = cols[8].split("\\|");
			
			Set<String> expRefIdSet = new HashSet<String>();
			String detectioniMethod = cols[6];
			// these terms were deprecated
			if (getMiDesc(detectioniMethod).equals("in vitro") || getMiDesc(detectioniMethod).equals("in vivo")) {
				detectioniMethod = "-";
			}
			for (String pmid : pmids) {
				expRefIdSet.add(getExperiment(pmid, cols[7], detectioniMethod, cols[28]));
			}
			List<String> expRefIds = new ArrayList<String>(expRefIdSet);
			Collections.sort(expRefIds);

			String role1 = getMiDesc(cols[18]);
			String role2 = getMiDesc(cols[19]);
			
			if (cols[0].equals(cols[1]) || StringUtils.join(geneASet, "_").equals(StringUtils.join(geneBSet, "_"))) {
				// self-interaction
				for (String geneA : geneASet) {
					String geneARef = getGene(geneA, cols[9]);
					
					for (String expRefId : expRefIds) {
						String intKey = String.format("%s_%s_%s", geneA, geneA, expRefId);
						if (detailMap.get(intKey) == null) {
							Item interaction = getInteraction(geneARef, geneARef);
							Item detail = createItem("InteractionDetail");
							
							if (role1 != null) {
								detail.setAttribute("role1", role1);
							}
							if (role2 != null) {
								detail.setAttribute("role2", role2);
							}
							String relationshipType = null;
							String intType = null;
							if (!cols[11].equals("-")) {
								String miId = getPsiMiId(cols[11]);
								relationshipType = getInteractionTerm(miId);
								detail.setReference("relationshipType", relationshipType);
								// physical or genetic
								String interactionType = interactionTypeMap.get(miId);
								if (interactionType != null) {
									intType = interactionType;
								} else {
									LOG.error(String.format("Cannot resolve interaction type: %s", miId));
								}
							}
							if (intType == null) {
								intType = "unspecified";
							}
							detail.setAttribute("type", intType);
							detail.setReference("experiment", expRefId);
							detail.setAttribute("name", String.format("iRef:%s-%s", geneA, geneA));
							detail.addToCollection("allInteractors", geneARef);
							detail.setReference("interaction", interaction);
							store(detail);
							detailMap.put(intKey, detail.getIdentifier());
						}
						
						createInteractionSource(sourceDb, sourceId, Arrays.asList(detailMap.get(intKey)));
					}
				}
				
			} else {
				for (String geneA : geneASet) {
					for (String geneB : geneBSet) {
						String geneARef = getGene(geneA, cols[9]);
						String geneBRef = getGene(geneB, cols[10]);
						
						for (String expRefId : expRefIds) {
							String intKey = String.format("%s_%s_%s", geneA, geneB, expRefId);
							String intKey2 = String.format("%s_%s_%s", geneB, geneA, expRefId);
							if (detailMap.get(intKey) == null) {
								// detailMap.get(intKey2) must be null
								
								// A-B
								Item interaction = getInteraction(geneARef, geneBRef);
								Item detail = createItem("InteractionDetail");
								if (role1 != null) {
									detail.setAttribute("role1", role1);
								}
								if (role2 != null) {
									detail.setAttribute("role2", role2);
								}
								String relationshipType = null;
								String intType = null;
								if (!cols[11].equals("-")) {
									String miId = getPsiMiId(cols[11]);
									relationshipType = getInteractionTerm(miId);
									detail.setReference("relationshipType", relationshipType);
									// physical or genetic
									String interactionType = interactionTypeMap.get(miId);
									if (interactionType != null) {
										intType = interactionType;
									} else {
										LOG.error(String.format("Cannot resolve interaction type: %s", miId));
									}
								}
								if (intType == null) {
									intType = "unspecified";
								}
								detail.setAttribute("type", intType);
								detail.setReference("experiment", expRefId);
								detail.setAttribute("name", String.format("iRef:%s-%s", geneA, geneB));
								detail.addToCollection("allInteractors", geneARef);
								detail.addToCollection("allInteractors", geneBRef);
								detail.setReference("interaction", interaction);
								store(detail);
								detailMap.put(intKey, detail.getIdentifier());
								
								// B-A
								Item interaction2 = getInteraction(geneBRef, geneARef);
								Item detail2 = createItem("InteractionDetail");
								if (role1 != null) {
									detail2.setAttribute("role2", role1);
								}
								if (role2 != null) {
									detail2.setAttribute("role1", role2);
								}
								if (relationshipType != null) {
									detail2.setReference("relationshipType",relationshipType);
								}
								detail2.setAttribute("type", intType);
								detail2.setReference("experiment", expRefId);
								detail2.setAttribute("name", String.format("iRef:%s-%s", geneB, geneA));
								detail2.addToCollection("allInteractors", geneARef);
								detail2.addToCollection("allInteractors", geneBRef);
								detail2.setReference("interaction", interaction2);
								store(detail2);
								detailMap.put(intKey2, detail2.getIdentifier());
							}
							
							createInteractionSource(sourceDb, sourceId, Arrays.asList(detailMap.get(intKey), detailMap.get(intKey2)));
						}
						
					}
				
			}
			
			
			}
			
		}

	}

	private String getMiDesc(String s) {
		if (s.indexOf('(') == -1 || s.indexOf(')') == -1) {
			return "-";
		}
		return s.substring(s.indexOf('(') + 1, s.indexOf(')'));
	}

    private Item getInteraction(String refId, String gene2RefId) throws ObjectStoreException {
        MultiKey key = new MultiKey(refId, gene2RefId);
        Item interaction = intMap.get(key);
        if (interaction == null) {
            interaction = createItem("Interaction");
            interaction.setReference("gene1", refId);
            interaction.setReference("gene2", gene2RefId);
            intMap.put(key, interaction);
            store(interaction);
        }
        return interaction;
    }

	private void readInteractionType() throws IOException {
		interactionTypeMap = new HashMap<String, String>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(getClass()
				.getClassLoader().getResourceAsStream(TYPE_FILE)));
		String line;
		String type = "";
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.startsWith("//")) {
				continue;
			}
			if (line.startsWith("#")) {
				type = line.substring(1);
			} else {
				interactionTypeMap.put(line, type);
			}
		}

	}

	private String getPublication(String pubMedId) throws ObjectStoreException {
		String itemId = pubMap.get(pubMedId);
		if (itemId == null) {
			Item pub = createItem("Publication");
			pub.setAttribute("pubMedId", pubMedId);
			itemId = pub.getIdentifier();
			pubMap.put(pubMedId, itemId);
			store(pub);
		}
		return itemId;
	}

	private String getGene(String geneId, String taxonField) throws ObjectStoreException {
		String ret = geneMap.get(geneId);
		if (ret == null) {
			Item gene = createItem("Gene");
			gene.setAttribute("primaryIdentifier", geneId);
			if (!taxonField.equals("-")) {
				// example: taxid:9606(Homo sapiens)
				String taxonId = taxonField.substring(6, taxonField.indexOf("("));
				gene.setReference("organism", getOrganism(taxonId));
			}
			store(gene);
			ret = gene.getIdentifier();
			geneMap.put(geneId, ret);
		}
		return ret;
	}

	private String getInteractionTerm(String miId) throws ObjectStoreException {
		String itemId = miMap.get(miId);
		if (itemId == null) {
			Item term = createItem("InteractionTerm");
			term.setAttribute("identifier", miId);
			itemId = term.getIdentifier();
			miMap.put(miId, itemId);
			store(term);
		}
		return itemId;
	}

	private void createInteractionSource(String sourceDb, String sourceId, List<String> detailRefIds) throws ObjectStoreException {
		Item item = createItem("InteractionSource");
		item.setAttribute("sourceDb", sourceDb);
		item.setAttribute("sourceId", sourceId);
		for (String refId : detailRefIds) {
			item.addToCollection("details", refId);
		}
		store(item);
	}

	private String getExperiment(String pubMedId, String author, String detectioniMethod, String host)
			throws ObjectStoreException {
		// chenyian: seems that one experiment only associates with one detection method so far (2015.4.15) 
		MultiKey key = new MultiKey(pubMedId, detectioniMethod);
		String ret = expMap.get(key);
		if (ret == null) {
			Item exp = createItem("InteractionExperiment");
			if (!author.equals("-")) {
				exp.setAttribute("name", author);
			}
			
			if (!pubMedId.equals("-")) {
				// example: pubmed:10998417
				exp.setReference("publication", getPublication(pubMedId.substring(pubMedId.indexOf(':') + 1)));
			}
			if (!detectioniMethod.equals("-")) {
				// example: psi-mi:"MI:0007"(anti tag coimmunoprecipitation) 
				exp.addToCollection("interactionDetectionMethods", getInteractionTerm(getPsiMiId(detectioniMethod)));
			}

			// extra attributes
			if (host.startsWith("taxid:")) {
				String desc = host.substring(host.indexOf("(") + 1, host.length() - 1);
				if (desc.equals("-")) {
					desc = host.substring(0, host.indexOf("("));
				}
				if (SPECIAL_TAXON_ID_MAP.get(desc) != null) {
					exp.setAttribute("hostOrganism", String.format("%s (%s)", desc, SPECIAL_TAXON_ID_MAP.get(desc)));
				} else {
					exp.setAttribute("hostOrganism", desc);
				}
			}

			ret = exp.getIdentifier();
			expMap.put(key, ret);
			store(exp);
		}
		return ret;
	}

	private Set<String> processAltIdentifier(String altIdentifier) {
		Set<String> ret = new HashSet<String>();
		String[] ids = altIdentifier.split("\\|");
		for (String id : ids) {
			if (id.startsWith("entrezgene/locuslink:")) {
				ret.add(id.substring(id.indexOf(":") + 1));
			}
		}
		return ret;
	}

	private String getPsiMiId(String text) {
		return text.substring(text.indexOf("MI:"), text.indexOf("\"("));
	}

	private static Map<String, String> SPECIAL_TAXON_ID_MAP = new HashMap<String, String>();
	{
		// "taxid:-1" (in vitro), "taxid:-3" (unknown), "taxid:-4" (in vivo)
		// BTW, taxid:-2(chemical synthesis), taxid:-5(in sillico)
		SPECIAL_TAXON_ID_MAP.put("taxid:-1", "in vitro");
		SPECIAL_TAXON_ID_MAP.put("taxid:-2", "chemical synthesis");
		SPECIAL_TAXON_ID_MAP.put("taxid:-3", "unknown");
		SPECIAL_TAXON_ID_MAP.put("taxid:-4", "in vivo");
		SPECIAL_TAXON_ID_MAP.put("taxid:-5", "in sillico");
	}
}
