package org.intermine.bio.dataconversion;

import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.metadata.StringUtil;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

/**
 * 
 * @author chenyian
 */
public class GeneOrthologsConverter extends BioFileConverter {
	private static final Logger LOG = LogManager.getLogger(GeneOrthologsConverter.class);
	//
	private static final String DATASET_TITLE = "Gene";
	private static final String DATA_SOURCE_NAME = "NCBI";

	private Set<String> taxonIds;

	public void setOrthologOrganisms(String idListString) {
		this.taxonIds = new HashSet<String>(Arrays.asList(StringUtil.split(idListString, " ")));
		LOG.info("Setting list of organisms to " + this.taxonIds);
		System.out.println("Setting list of organisms to " + this.taxonIds);
	}

	private Map<String, String> geneMap = new HashMap<String, String>();

	/**
	 * Constructor
	 * 
	 * @param writer
	 *            the ItemWriter used to handle the resultant items
	 * @param model
	 *            the Model
	 */
	public GeneOrthologsConverter(ItemWriter writer, Model model) {
		super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
	}

	/**
	 * 
	 *
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
		Map<String, Set<String>> clusterMap = new HashMap<String, Set<String>>();
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			if (taxonIds.contains(cols[0]) && taxonIds.contains(cols[3])) {
				String key = String.format("%s_%s", cols[0], cols[1]);
				if (clusterMap.get(key) == null) {
					clusterMap.put(key, new HashSet<String>());
				}
				clusterMap.get(key).add(String.format("%s_%s", cols[3], cols[4]));
			}
		}
		for (String key : clusterMap.keySet()) {
			Item item = createItem("Homology");
			item.setAttribute("identifier", key);
			Set<String> geneSet = clusterMap.get(key);
			for (String geneEntry : geneSet) {
				String[] split = geneEntry.split("_");
				item.addToCollection("genes", getGene(split[1], split[0]));
			}
			String[] split = key.split("_");
			item.addToCollection("genes", getGene(split[1], split[0]));
			store(item);
		}
	}

	private String getGene(String geneId, String taxonId) throws ObjectStoreException {
		String ret = geneMap.get(geneId);
		if (ret == null) {
			Item item = createItem("Gene");
			item.setAttribute("primaryIdentifier", geneId);
			item.setReference("organism", getOrganism(taxonId));
			ret = item.getIdentifier();
			store(item);
			geneMap.put(geneId, ret);
		}
		return ret;
	}
}
