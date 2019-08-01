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
 * @author chenyian
 */
public class Barcode30SampleConverter extends BioFileConverter
{
//	private static final Logger LOG = Logger.getLogger(Barcode30SampleConverter.class);
	//
	private static final String DATASET_TITLE = "Barcode 3.0";
	private static final String DATA_SOURCE_NAME = "Barcode";

	private Map<String, String> platformMap = new HashMap<String, String>();
	private Map<String, String> tissueMap = new HashMap<String, String>();
	private Map<String, String> seriesMap = new HashMap<String, String>();

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public Barcode30SampleConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
		String fn = getCurrentFile().getName();
		String platformId = "";
		for (String part : fn.split("-")) {
			if (part.toLowerCase().startsWith("gpl")) {
				platformId = part.toUpperCase();
			}
		}
		if (platformId.equals("")) {
			throw new RuntimeException("Unexpected file name: " + fn
					+ ". Unable to identify the platform.");
		}
		String platform = getPlatform(platformId);

		// start to parse the file
		Iterator<String[]> iterator = FormattedTextParser.parseCsvDelimitedReader(reader);
		String[] header = iterator.next(); 
		Map<String, Item> sampleItemMap = new HashMap<String, Item>();
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			String sampleId = cols[2];
			
			Item item = sampleItemMap.get(sampleId);
			if (item == null) {
				item = createItem("MicroarraySample");
				item.setAttribute("identifier", sampleId);
				item.setReference("platform", platform);
				
				sampleItemMap.put(sampleId, item);
			}
			
			for (String seriesId : cols[3].split(";")) {
				item.addToCollection("series", getSeries(seriesId));
			}
			
			String tissueName = cols[4];
			if (header[5].equals("celltype") && !cols[5].equals("NA") && !cols[5].equals("cell_line") && !cols[5].equals(tissueName)) {
				tissueName = tissueName + ":" + cols[5];
			}
			item.addToCollection("tissues", getTissue(tissueName));
		}
		store(sampleItemMap.values());
    }

	private String getPlatform(String platformId) throws ObjectStoreException {
		String ret = platformMap.get(platformId);
		if (ret == null) {
			Item item = createItem("MicroarrayPlatform");
			item.setAttribute("identifier", platformId);
			store(item);
			ret = item.getIdentifier();
			platformMap.put(platformId, ret);
		}
		return ret;
	}
	
	private String getTissue(String tissueName) throws ObjectStoreException {
		String ret = tissueMap.get(tissueName);
		if (ret == null) {
			Item item = createItem("BarcodeTissue");
			item.setAttribute("identifier", tissueName);
			store(item);
			ret = item.getIdentifier();
			tissueMap.put(tissueName, ret);
		}
		return ret;
	}
	
	private String getSeries(String seriesId) throws ObjectStoreException {
		String ret = seriesMap.get(seriesId);
		if (ret == null) {
			Item item = createItem("MicroarraySeries");
			item.setAttribute("identifier", seriesId);
			store(item);
			ret = item.getIdentifier();
			seriesMap.put(seriesId, ret);
		}
		return ret;
	}
}
