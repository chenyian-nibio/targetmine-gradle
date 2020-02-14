package org.intermine.bio.dataconversion;

import java.io.BufferedReader;
import java.io.Reader;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.xml.full.Item;


/**
 * 
 * @author chenyian
 */
public class KgmlFileConverter extends BioFileConverter
{
    //
	private static final String DATASET_TITLE = "KEGG Pathway";
	private static final String DATA_SOURCE_NAME = "KEGG";

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public KgmlFileConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
		String fileName = getCurrentFile().getName();
		String pathwayId = fileName.substring(0, fileName.indexOf("."));

		BufferedReader br = new BufferedReader(reader);
		String line;

		StringBuffer sb = new StringBuffer();
		while ((line = br.readLine()) != null) {
			sb.append(line);
		}
		
		Item graphItem = createItem("PathwayGraph");
		graphItem.setAttribute("identifier", pathwayId);
		graphItem.setAttribute("kgmlFile", sb.toString());
		store(graphItem);
		
		Item pathwayItem = createItem("Pathway");
		pathwayItem.setAttribute("identifier", pathwayId);
		pathwayItem.setReference("graph", graphItem);
		store(pathwayItem);
    }
}
