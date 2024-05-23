package org.intermine.bio.dataconversion;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.intermine.dataconversion.FileConverter;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.xml.full.Item;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 
 * @author chenyian
 */
public class KeggJtcConverter extends FileConverter {
	
	protected static final Logger LOG = LogManager.getLogger(KeggJtcConverter.class);
	//
	Map<String, Integer> codeMap = new HashMap<String, Integer>();
	
	/**
	 * Constructor
	 * @param writer the ItemWriter used to handle the resultant items
	 * @param model the Model
	 */
	public KeggJtcConverter(ItemWriter writer, Model model) {
		super(writer, model);
		codeMap.put("A", 1);
		codeMap.put("B", 2);
		codeMap.put("C", 3);
		codeMap.put("D", 4);
	}

	/**
	 * 
	 *
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		String currentFileName = getCurrentFile().getName();
		if (currentFileName.equals("br08301.keg")) {
			BufferedReader in = new BufferedReader(reader);
			String line;
			String currentClassId = "";
			while ((line = in.readLine()) != null) {
				if (line.startsWith("A")) {
					String replaceAll = line.replaceAll("<\\/?b>", " ");
					String[] split = replaceAll.split("\\s+", 3);
					createJscClassification(split[1], split[2], null);
				} else if (line.startsWith("D") || line.startsWith("C") || line.startsWith("B")) {
					String[] split = line.split("\\s+", 3);
					String id = split[1];
					String parentId = id.substring(0, id.length()-1);
					createJscClassification(id, split[2], parentId);
					currentClassId = id;
				} else if (line.startsWith("E")) {
					String keggDrugId = line.substring(9, 15);
					if (currentClassId.length() == 4) {
						Item drugCompound = getKeggDrugCompound(keggDrugId);
						drugCompound.addToCollection("jsccCodes", jsccMap.get(currentClassId));

					} else {
						throw new RuntimeException("illeagle format at line: " + line);
					}
				}
			}
			in.close();
		} else if (currentFileName.equals("br08302.keg")) {
			// #DEFINITION  USP Drug Classification
			BufferedReader in = new BufferedReader(reader);
			String line;
			Map<String, String> parentMap = new HashMap<String, String>();
			while ((line = in.readLine()) != null) {
				if (line.startsWith("A")) {
					parentMap.clear();
					String classA = createUspClassification("A", line.substring(1), null);
					parentMap.put("A", classA);
				} else if (line.substring(0, 1).matches("[BCDE]")) {
					if (line.matches(".+\\sD\\d{5}\\s{2}.+")) {
						String[] split = line.split("\\s+", 3);
						String keggDrugId = split[1].trim();
						if (!parentMap.isEmpty()) {
							List<String> keys = new ArrayList<String>(parentMap.keySet());
							Collections.sort(keys);
							String parentId = parentMap.get(keys.get(keys.size() - 1));
							Item drugCompound = getKeggDrugCompound(keggDrugId);
							drugCompound.addToCollection("uspClassifications", parentId);
						}
					} else {
						String[] split = line.split("\\s+", 2);
						if (split.length == 2) {
							String name = split[1];
							name = name.replaceAll("\\[DG:DG.+\\]", "");
							String code = line.substring(0, 1);
							String classId = createUspClassification(code, name.trim(), parentMap);
							parentMap.put(code, classId);
						}
					}
				}	
			}
			in.close();
		} else if (currentFileName.equals("br08310.keg")) {
			BufferedReader in = new BufferedReader(reader);
			String line;
			Map<String, String> parentMap = new HashMap<String, String>();;
			List<String> genes = new ArrayList<String>();
			String target = "";
			while ((line = in.readLine()) != null) {
				if (line.startsWith("C") || line.startsWith("B") || line.startsWith("A")) {
					String replaceAll = line.replaceAll("<\\/?b>", " ");
					String[] split = replaceAll.split("\\s+", 2);
					String lv = split[0];
					String name = split[1].trim();
					if (lv.equals("C")) {
						parentMap.put(lv, getTargetBasedClassification(codeMap.get(lv), name, parentMap.get("B")));
					} else if (lv.equals("B")) {
						parentMap.put(lv, getTargetBasedClassification(codeMap.get(lv), name, parentMap.get("A")));
					} else if (lv.equals("A")) {
						parentMap.put(lv, getTargetBasedClassification(codeMap.get(lv), name, null));
					} else {
						throw new RuntimeException("Unexpected level: '" + lv + "' at the line: '" + line + "'.");
					} 
				} else if (line.startsWith("D")) {
					Pattern p = Pattern.compile("\\s+(.+)\\s+\\[HSA:(.+)\\]\\s");
					Matcher m = p.matcher(line);
					if (m.find()) {
						target = m.group(1);
						String[] geneIds = m.group(2).split(" ");
						genes = new ArrayList<String>();
						for(String id : geneIds) {
							genes.add(getGene(id));
						}
					} else {
						System.err.println("No match. " + line);
					}
				} else if (line.startsWith("E")) {
					String keggDrugId = line.substring(9, 15);
					Item drugCompound = getKeggDrugCompound(keggDrugId);
					
					Item annotation = createItem("TbcAnnotation");
					annotation.setAttribute("target", target);
					annotation.setReference("classification", parentMap.get("C"));
					for (String g : genes) {
						annotation.addToCollection("genes", g);
					}
					annotation.setReference("drug", drugCompound);
					store(annotation);
				} else {
					// throw new RuntimeException("Unexpected initial at the line: '" + line + "'.");
					System.out.println("Skip the line: '" + line + "'");
				}
			}
			in.close();

		} else {
			System.out.println("Unexpected file: " + currentFileName + ", skip it.");
			return;
		}

	}

	@Override
	public void close() throws Exception {
		store(drugCompoundMap.values());
	}

	private Map<String, Item> drugCompoundMap = new HashMap<String, Item>();

    private Item getKeggDrugCompound(String keggDrugId) throws ObjectStoreException {
    	Item ret = drugCompoundMap.get(keggDrugId);
    	if (ret == null) {
    		ret = createItem("DrugCompound");
    		ret.setAttribute("identifier", String.format("KEGG DRUG: %s", keggDrugId));
    		ret.setAttribute("keggDrugId", keggDrugId);
    		drugCompoundMap.put(keggDrugId, ret);
    	}
    	return ret;
    }
    
    private Map<String, String> jsccMap = new HashMap<String, String>();
    
    private String createJscClassification(String jsccCode, String name, String parentId) throws ObjectStoreException {
    	String ret = jsccMap.get(jsccCode);
    	if (ret == null) {
    		Item item = createItem("JscClassification");
    		item.setAttribute("jsccCode", jsccCode);
    		item.setAttribute("name", name);
    		if (parentId != null) {
    			item.setReference("parent", jsccMap.get(parentId));
    			
    			for (int i = 0; i < parentId.length(); i++) {
    				item.addToCollection("allParents", jsccMap.get(parentId.substring(0, i + 1)));
				}
    			item.addToCollection("allParents", item);
    		}
    		store(item);
    		ret = item.getIdentifier();
    		jsccMap.put(jsccCode, ret);
    	}
    	return ret;
    }

    private String createUspClassification(String code, String name, Map<String, String> parentMap) throws ObjectStoreException {
    	Item item = createItem("UspClassification");
    	item.setAttribute("name", name);
    	Integer value = codeMap.get(code);
    	if (parentMap != null && parentMap.size() > 0) {
    		List<String> keys = new ArrayList<String>(parentMap.keySet());
    		Collections.sort(keys);
    		item.setReference("parent", parentMap.get(keys.get(value - 2)));
    		for (int i = 0; i < value - 1; i++) {
    			item.addToCollection("allParents", parentMap.get(keys.get(i)));
			}
    	}
		item.setAttribute("lv", value.toString());
    	store(item);
    	String ret = item.getIdentifier();
    	return ret;
    }

	private Map<String, String> tbcMap = new HashMap<String, String>();
	private String getTargetBasedClassification(Integer level, String name, String parentRefId) throws ObjectStoreException {
		String ret = tbcMap.get(name);
		if (ret == null) {
			Item item = createItem("TargetBasedClassification");
			item.setAttribute("lv", String.valueOf(level));
			item.setAttribute("name", name);
			if (parentRefId != null) {
				item.setReference("parent", parentRefId);
			}
			store(item);
			ret = item.getIdentifier();
			tbcMap.put(name, ret);
		}
		return ret;
	}

	private Map<String, String> geneMap = new HashMap<String, String>();
	private String getGene(String geneId) throws ObjectStoreException {
		String ret = geneMap.get(geneId);
		if (ret == null) {
			Item item = createItem("Gene");
			item.setAttribute("primaryIdentifier", geneId);
			store(item);
			ret = item.getIdentifier();
			geneMap.put(geneId, ret);
		}
		return ret;
	}

}
