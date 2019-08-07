package org.intermine.bio.dataconversion;

import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

/**
 * 
 * @author
 */
public class GeneMedgenConverter extends BioFileConverter {
	//
	private static final String DATASET_TITLE = "Gene";
	private static final String DATA_SOURCE_NAME = "NCBI";

	/**
	 * Constructor
	 * 
	 * @param writer the ItemWriter used to handle the resultant items
	 * @param model  the Model
	 */
	public GeneMedgenConverter(ItemWriter writer, Model model) {
		super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
	}

	/**
	 * 
	 *
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {

		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
		
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			// we only want phenotypes, not genes
			String type = cols[2];
			if (type.equals("gene") || "-".equals(cols[1])) {
				continue;
			}
//			String omimId = cols[0];
			String geneId = cols[1];
			String medGeneCui = cols[4];
			
    		Item item = createItem("Disease");
    		item.setReference("diseaseTerm", getDiseaseTerm(medGeneCui));
    		item.setReference("gene", getGene(geneId));
    		item.addToCollection("sources", getDataSource(DATA_SOURCE_NAME));
    		store(item);

		}

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
}
