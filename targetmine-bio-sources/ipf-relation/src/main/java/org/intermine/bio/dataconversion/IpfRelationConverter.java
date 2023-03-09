package org.intermine.bio.dataconversion;

import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;

import org.apache.commons.lang.StringUtils;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;


/**
 * 
 * @author chenyian
 */
public class IpfRelationConverter extends BioFileConverter {
    //
    private static final String DATASET_TITLE = "IPF";
    private static final String DATA_SOURCE_NAME = "TargetMine";

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public IpfRelationConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
    	String fileName = getCurrentFile().getName();
    	String no = fileName.substring(0, 1);
    	
    	Iterator<String[]> iterator = FormattedTextParser.parseCsvDelimitedReader(reader);
    	iterator.next();
    	iterator.next();
    	while (iterator.hasNext()) {
    		String[] cols = iterator.next();
    		if (StringUtils.isEmpty(cols[0])) {
    			continue;
    		}
    		int sno = Integer.valueOf(cols[0]);
    		String pubmedId = cols[1];
    		String umlsCode = cols[13];
    		String fromGid = cols[18];
    		String fromAnalysis = cols[24];
    		String fromAlteration = cols[25];
//    		String fromEffect = cols[26];
    		String toGid = cols[31];
    		String toAnalysis = cols[34];
    		String toAlteration = cols[35];
//    		String phenotypeStd = cols[37];
//    		String phenotypeAlteration = cols[39];
    		
    		String ipfId = String.format("%s%05d", no, sno);
			String text = String.format("%s %s -> %s %s", fromAnalysis, fromAlteration, toAnalysis, toAlteration);
			Set<String> fromSet = new HashSet<String>(Arrays.asList(fromGid.split("\\||;\\s|,\\s|\\s")));
			Set<String> toSet = new HashSet<String>(Arrays.asList(toGid.split("\\||;\\s|,\\s|\\s")));
    		
    		// relations
			for (String gene1 : fromSet) {
				if (gene1.equals("[NA]")) {
					continue;
				}
				for (String gene2 : toSet) {
					if (gene2.equals("[NA]")) {
						continue;
					}
					// Item relation = createItem("Relation");
					// relation.setAttribute("name", String.format("%s->%s", gene1, gene2));
					// relation.setReference("gene1", getGene(gene1));
					// relation.setReference("gene2", getGene(gene2));
					// relation.setAttribute("text", text);
					// relation.setReference("reference", getIPF(ipfId));
					// store(relation);
					getIPFRelation(gene1, gene2, text).addToCollection("details", getIPF(ipfId));
				}
			}
			// disease associations
			for (String gene : fromSet) {
				if (!gene.equals("[NA]")) {
					getDisease(gene, umlsCode).addToCollection("publications", getPublication(pubmedId));
				}
			}
			for (String gene : toSet) {
				if (!gene.equals("[NA]")) {
					getDisease(gene, umlsCode).addToCollection("publications", getPublication(pubmedId));
				}
			}
		}
    }
    
    @Override
    public void close() throws Exception {
    	store(relationMap.values());
		store(diseaseMap.values());
    }

	private Map<String, Item> relationMap = new HashMap<String, Item>();
	private Item getIPFRelation(String gene1, String gene2, String text) throws ObjectStoreException {
		String key = String.format("%s-%s-%s", gene1, gene2, text);
		Item ret = relationMap.get(key);
		if (ret == null) {
			ret = createItem("IPFRelation");
			ret.setAttribute("name", String.format("%s->%s", gene1, gene2));
			ret.setReference("gene1", getGene(gene1));
			ret.setReference("gene2", getGene(gene2));
			ret.setAttribute("text", text);
			relationMap.put(key, ret);
		}
		return ret;
	}

	private Map<String, Item> diseaseMap = new HashMap<String, Item>();
	private Item getDisease(String geneId, String diseaseId) throws ObjectStoreException {
		String diseaseKey = String.format("%s-%s", geneId, diseaseId);
		Item ret = diseaseMap.get(diseaseKey);
		if (ret == null) {
			ret = createItem("Disease");
			ret.setReference("diseaseTerm", getDiseaseTerm(diseaseId));
			ret.setReference("gene", getGene(geneId));
			diseaseMap.put(diseaseKey, ret);
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
	
	private Map<String, String> diseaseTermMap = new HashMap<String, String>();
	private String getDiseaseTerm(String identifier) throws ObjectStoreException {
		String ret = diseaseTermMap.get(identifier);
		if (ret == null) {
			Item item = createItem("DiseaseTerm");
			item.setAttribute("identifier", identifier);
			store(item);
			ret = item.getIdentifier();
			diseaseTermMap.put(identifier, ret);
		}
		return ret;
	}

	private Map<String, String> ipfMap = new HashMap<String, String>();
	private String getIPF(String identifier) throws ObjectStoreException {
		String ret = ipfMap.get(identifier);
		if (ret == null) {
			Item item = createItem("IPF");
			item.setAttribute("identifier", identifier);
			store(item);
			ret = item.getIdentifier();
			ipfMap.put(identifier, ret);
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
}
