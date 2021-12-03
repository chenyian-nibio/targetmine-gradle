package org.intermine.bio.dataconversion;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

/**
 * Read data from the flat file 'drug' from kegg drug. This parser is  
 * supposed to be run after drugbank, if both of them are required. 
 * 
 * @author chenyian
 * 
 */
public class KeggDrugConverter extends BioFileConverter {
	protected static final Logger LOG = LogManager.getLogger(KeggDrugConverter.class);
	//
	private static final String DATASET_TITLE = "KEGG Drug";
	private static final String DATA_SOURCE_NAME = "KEGG";

	private Set<String> synonymTypeSet = new HashSet<String>();
	
	private Set<String> therapeuticLabels = new HashSet<String>();
	/**
	 * Constructor
	 * 
	 * @param writer
	 *            the ItemWriter used to handle the resultant items
	 * @param model
	 *            the Model
	 */
	public KeggDrugConverter(ItemWriter writer, Model model) {
		super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
//		JAN - Japanese Approved Name
//		USAN - United States Adopted Name
//		NF - National Formulary drug name
//		INN - International Nonproprietary Name
//		BAN - British Approved Name
//		DCF - Denomination commune francaise
//		JP17 - Japanese Pharmacopoeia, 17th edition
//		USP - United States Pharmacopeia
//		non-JP - Japanese standards for non-Pharmacopoeial crude drugs
		synonymTypeSet.addAll(Arrays.asList("JAN", "USAN", "NF", "INN", "BAN", "DCF", "JP17",
				"USP", "non-JP"));
		
	}

	private File inchikeyFile;
	private File inchiFile;

	public void setInchikeyFile(File inchikeyFile) {
		this.inchikeyFile = inchikeyFile;
	}
	public void setInchiFile(File inchiFile) {
		this.inchiFile = inchiFile;
	}
	
	private Map<String, String> inchiKeyMap = new HashMap<String, String>();
	private Map<String, String> inchiMap = new HashMap<String, String>();

	/**
	 * 
	 * 
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		readInchikeyFile();
		readInchiFile();
		createAllTherapeuticClassifications();

		BufferedReader in = null;
		try {
			in = new BufferedReader(reader);
			String line;
			String keggDrugId = "";
			String name = "";
			List<String> allNames = new ArrayList<String>();
			String atcCodes = "";
			Map<String, Set<String>> metabolisms = new HashMap<String, Set<String>>();
			Map<String, Set<String>> interactions = new HashMap<String, Set<String>>();
			
			boolean isName = false;
			boolean isMetabolism = false;
			boolean isInteraction = false;
			
			while ((line = in.readLine()) != null) {
				if (line.startsWith("METABOLISM")) {
					isMetabolism = true;
					isInteraction = false;
				} else if (line.startsWith("INTERACTION")) {
					isInteraction = true;
					isMetabolism = false;
				} else if (!line.startsWith(" ")) {
					isInteraction = false;
					isMetabolism = false;
				}
				
				if (isName) {
					if (line.startsWith(" ")) {
						allNames.add(line.substring(12).trim());
					} else {
						isName = false;
					}
				}
				
				if (line.startsWith("ENTRY")) {
					String[] split = line.split("\\s+");
					keggDrugId = split[1];
				} else if (line.startsWith("NAME")) {
					// TODO to be refined
					name = line.substring(12).replaceAll(";$", "").replaceAll("\\s\\(.+?\\)$","").trim();
					allNames.add(line.substring(12).trim());
					isName = true;
				} else if (line.contains("ATC code:")) {
					atcCodes = line.substring(line.indexOf(":") + 2);
				} else if (line.contains(".html]")) {
					String refHtml = line.substring(line.indexOf("[br") + 1, line.indexOf(".html]"));
					if (therapeuticClassMap.keySet().contains(refHtml)) {
						therapeuticLabels.add(refHtml);
					}
				} else if (isMetabolism) {
					String content = line.substring(12).trim();
					if (!StringUtils.isEmpty(content)) {
						String[] entries;
						Set<String> geneIds = new HashSet<String>();
						String type = "Undefined";
						if (content.contains(": ")) {
							String[] split = content.split(":\\s", 2);
							entries = split[1].split(",\\s");
							type = split[0];
						} else {
							entries = content.split(",\\s");
						}
						for (String entry : entries) {
							// suppose they are all human genes thus formatted like [HSA:xxx xxx]
							int startPos = entry.indexOf("A:");
							if (startPos != -1) {
								String substring = entry.substring(startPos+2, entry.length()-1);
								if (substring.contains(" ")) {
									geneIds.addAll(Arrays.asList(substring.split("\\s")));
								} else {
									geneIds.add(substring);
								}
							}
						}
						if (!geneIds.isEmpty()) {
							metabolisms.put(type, geneIds);
						}
					}
				} else if (isInteraction) {
					String content = line.substring(12).trim();
					if (!StringUtils.isEmpty(content)) {
						String[] entries;
						Set<String> geneIds = new HashSet<String>();
						String type = "Undefined";
						if (content.contains(": ")) {
							String[] split = content.split(":\\s", 2);
							entries = split[1].split(",\\s");
							type = split[0];
						} else {
							entries = content.split(",\\s");
						}
						for (String entry : entries) {
							// suppose they are all human genes thus formatted like [HSA:xxx xxx]
							int startPos = entry.indexOf("A:");
							if (startPos != -1) {
								String substring = entry.substring(startPos+2, entry.length()-1);
								if (substring.contains(" ")) {
									geneIds.addAll(Arrays.asList(substring.split("\\s")));
								} else {
									geneIds.add(substring);
								}
							}
						}
						if (!geneIds.isEmpty()) {
							interactions.put(type, geneIds);
						}
					}
					
				} else if (line.startsWith("///")) {
					String inchiKey = inchiKeyMap.get(keggDrugId);
					
					List<Item> drugCompounds = new ArrayList<Item>();
					Item drugItem = createDrugCompound(String.format("KEGG DRUG: %s", keggDrugId), keggDrugId, name,
							atcCodes, inchiKey, keggDrugId);
					drugCompounds.add(drugItem);
					// add metabolisms & interactions
					if (!metabolisms.isEmpty()) {
						for (String key : metabolisms.keySet()) {
							for (String geneId : metabolisms.get(key)) {
								saveMetabolismAndInteraction("DrugMetabolism", key, geneId, drugItem);
							}
						}
					}
					if (!interactions.isEmpty()) {
						for (String key : interactions.keySet()) {
							for (String geneId : interactions.get(key)) {
								saveMetabolismAndInteraction("DrugInteraction", key, geneId, drugItem);
							}
						}
					}
					if (!allNames.isEmpty()) {
						for (String drugName: allNames) {
							// example: Dexamethasone (JP17/USP/INN);
							if (drugName.contains("(")) {
								Pattern pattern = Pattern.compile("(.+) \\((.+?)\\);?$");
								Matcher matcher = pattern.matcher(drugName);
								
								if (matcher.find()) {
									String value = matcher.group(1);
									String types = matcher.group(2);
									for (String type: types.split("/")) {
										Item syn = createItem("CompoundSynonym");
										syn.setAttribute("value", value);
										if (synonymTypeSet.contains(type) ||
												isSynonymType(type)) {
											syn.setAttribute("type", type);
										}
										syn.setReference("subject", drugItem);
										store(syn);
									}
								}
								
							} else {
								Item syn = createItem("CompoundSynonym");
								syn.setAttribute("value", drugName.replaceAll(";$", "").trim());
								syn.setReference("subject", drugItem);
								store(syn);
							}
						}
					}
					
					// clear current entry
					keggDrugId = "";
					name = "";
					allNames = new ArrayList<String>();
					atcCodes = "";
					
					therapeuticLabels.clear();
					
					metabolisms = new HashMap<String, Set<String>>();
					interactions = new HashMap<String, Set<String>>();

					isMetabolism = false;
					isInteraction = false;
				}
			}
		} catch (FileNotFoundException e) {
			LOG.error(e);
		} catch (IOException e) {
			LOG.error(e);
		} finally {
			if (in != null)
				in.close();
		}

	}
	
	private boolean isSynonymType(String type) {
		for (String st : synonymTypeSet) {
			if (type.startsWith(st) || type.endsWith(st)) {
				return true;
			}
		}
		return false;
	}

	private Item createDrugCompound(String identifier, String keggDrugId, String name, String atcCodes, String inchiKey,
			String originalId) throws ObjectStoreException {
		Item drugItem = createItem("DrugCompound");
		// the most important, the primary key
		drugItem.setAttribute("identifier", identifier);
		drugItem.setAttribute("keggDrugId", keggDrugId);
		if (StringUtils.isEmpty(name)) {
			name = "KEGG Drug " + keggDrugId;
			LOG.info("No name for KEGG Drug: " + keggDrugId);
		}
		drugItem.setAttribute("name", name);
		drugItem.setAttribute("genericName", name);
		drugItem.setAttribute("originalId", originalId);

		if (!atcCodes.equals("")) {
			for (String atcCode : atcCodes.split(" ")) {
				drugItem.addToCollection("atcCodes", getAtcClassification(atcCode, name));
			}
		}

		if (inchiKey != null) {
			drugItem.setAttribute("inchiKey", inchiKey);
			drugItem.setReference("compoundGroup",
					getCompoundGroup(inchiKey.substring(0, inchiKey.indexOf("-")), name));
		}
		
		for (String label : therapeuticLabels) {
			drugItem.addToCollection("therapeuticClassifications", therapeuticClassMap.get(label));
		}
		
		store(drugItem);

		String inchi = inchiMap.get(keggDrugId);
		if (inchi != null) {
			Item structure = createItem("CompoundStructure");
			structure.setAttribute("type", "InChI");
			structure.setAttribute("value", inchi);
			structure.setReference("compound", drugItem);
			store(structure);
		}
		return drugItem;
	}
	
	private Map<String, Set<String>> inchiKeyKeggDrugMap = new HashMap<String, Set<String>>();
	
	private void readInchikeyFile() {
		try {
			Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(new FileReader(inchikeyFile));
			
			while(iterator.hasNext()) {
				String[] cols = iterator.next();
				inchiKeyMap.put(cols[0], cols[1]);
				
				if (inchiKeyKeggDrugMap.get(cols[1]) == null) {
					inchiKeyKeggDrugMap.put(cols[1], new HashSet<String>());
				}
				inchiKeyKeggDrugMap.get(cols[1]).add(cols[0]);
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	private void readInchiFile() {
		try {
			Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(new FileReader(inchiFile));
			
			while(iterator.hasNext()) {
				String[] cols = iterator.next();
				inchiMap.put(cols[0], cols[1]);
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	private Map<String, String> atcMap = new HashMap<String, String>();

	private String getAtcClassification(String atcCode, String name) throws ObjectStoreException {
		String ret = atcMap.get(atcCode);
		if (ret == null) {
			Item item = createItem("AtcClassification");
			item.setAttribute("atcCode", atcCode);
			item.setAttribute("name", name);
			// TODO add parent
			String parentCode = atcCode.substring(0, 5);
			item.setReference("parent", getParent(parentCode));

			// TODO create parents; to be improved
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

	private Map<String, String> compoundGroupMap = new HashMap<String, String>();
	
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

	private Map<String, String> geneMap = new HashMap<String, String>();
	
	private String getGene(String geneId) throws ObjectStoreException {
		String ret = geneMap.get(geneId);
		if (ret == null) {
			Item item = createItem("Gene");
			item.setAttribute("primaryIdentifier", geneId);
			item.setAttribute("ncbiGeneId", geneId);
			store(item);
			ret = item.getIdentifier();
			geneMap.put(geneId, ret);
		}
		return ret;
	}
	
	private void saveMetabolismAndInteraction(String itemName, String key, String geneId,
			Item drugItem) throws ObjectStoreException {
		Item item = createItem(itemName);
		item.setAttribute("type", key);
		item.setReference("gene", getGene(geneId));
		item.setReference("drug", drugItem);
		store(item);

	}
	
	Map<String, String> therapeuticClassMap = new HashMap<String, String>(); 
	
	private void createTherapeuticClassification(String identifier, String name) throws ObjectStoreException {
		Item item = createItem("TherapeuticClassification");
		item.setAttribute("identifier", identifier);
		item.setAttribute("name", name);
		store(item);
		therapeuticClassMap.put(identifier, item.getIdentifier());
	}
	
	private void createAllTherapeuticClassifications() throws ObjectStoreException {
		createTherapeuticClassification("br08340", "Antineoplastics");
		createTherapeuticClassification("br08350", "Antibacterials");
		createTherapeuticClassification("br08351", "Antivirals");
		createTherapeuticClassification("br08352", "Antifungals");
		createTherapeuticClassification("br08353", "Antiparasitics");
		createTherapeuticClassification("br08361", "Antidiabetics");
		createTherapeuticClassification("br08365", "Hypolipidemic agents");
		createTherapeuticClassification("br08368", "Osteoporosis drugs");
		createTherapeuticClassification("br08364", "Cardiovascular agents");
		createTherapeuticClassification("br08363", "Psychiatric agents");
		createTherapeuticClassification("br08367", "Neurological agents");
		createTherapeuticClassification("br08362", "Anti-allergic agents");
		createTherapeuticClassification("br08366", "Antirheumatic and antigout drugs");
		createTherapeuticClassification("br08369", "Anesthetics, analgesics and anti-inflammatory drugs");
		createTherapeuticClassification("br08372", "Respiratory agents");
		createTherapeuticClassification("br08371", "Gastrointestinal agents");
		createTherapeuticClassification("br08373", "Endocrine and hormonal agents");
		createTherapeuticClassification("br08370", "Dermatological agents");
	}	 

}
