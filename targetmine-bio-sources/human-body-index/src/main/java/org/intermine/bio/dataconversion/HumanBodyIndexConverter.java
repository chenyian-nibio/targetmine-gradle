package org.intermine.bio.dataconversion;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;

/**
 * 
 * @author chenyian
 */
public class HumanBodyIndexConverter extends BioFileConverter {
	//
	private static final String DATASET_TITLE = "Human body index";
	private static final String DATA_SOURCE_NAME = "Human body index";

	private Map<String, String> probeSetMap = new HashMap<String, String>();
	private Map<String, String> tissueMap = new HashMap<String, String>();
	private Map<String, String> sampleMap = new HashMap<String, String>();
	private Map<String, String> sampleTissueMap = new HashMap<String, String>();

	private File sampleInfoFile;
	
	public void setSampleInfoFile(File sampleInfoFile) {
		this.sampleInfoFile = sampleInfoFile;
	}

	/**
	 * Constructor
	 * 
	 * @param writer the ItemWriter used to handle the resultant items
	 * @param model  the Model
	 */
	public HumanBodyIndexConverter(ItemWriter writer, Model model) {
		super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
	}

	/**
	 * 
	 *
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		if (sampleMap.isEmpty()) {
			readSampleInfoFile();
		}
		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
		// the available first line is headers (the comments will be skipped)
		String[] headers = iterator.next();
		
		// process the header
		// [adipose_tissue, GSM175994](raw)
		Pattern p = Pattern.compile("\\[.+, (GSM\\d+)\\]\\((\\w+)\\)");
		
		int startIndex = 0;
		int endIndex = 0;
		Map<String, Integer> callMap = new HashMap<String, Integer>();
		Map<Integer, String> sampleIdMap = new HashMap<Integer, String>();
		for (int i = 1; i < headers.length; i++) {
			Matcher matcher = p.matcher(headers[i]);
			if (matcher.matches()) {
				String sid = matcher.group(1);
				String tag = matcher.group(2);
				sampleIdMap.put(Integer.valueOf(i), sid);
				if (startIndex == 0 && tag.equals("normalized")) {
					startIndex = i;
				}
			} else {
				if (endIndex < startIndex) {
					endIndex = i;
				}
				// GSM175789.CEL_call
				if (headers[i].endsWith("CEL_call")) {
					String sid = headers[i].split("\\.")[0];
					callMap.put(sid, Integer.valueOf(i));
				}
			}
		}
		
		// process the expression values
		int count = 0;
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			String probeSetId = cols[0];
			if (probeSetId.startsWith("AFFX-")) {
				continue;
			}
			for (int i = startIndex; i < endIndex; i++) {
				String expressionValue = cols[i];
				String sid = sampleIdMap.get(Integer.valueOf(i));
				String call = cols[callMap.get(sid).intValue()];
				Item item = createItem("HbiExpression");
				item.setAttribute("value", expressionValue);
				item.setReference("probeSet", getProbSet(probeSetId));
				item.setReference("platform", platformRef);
				item.setReference("tissue", sampleTissueMap.get(sid));
				item.setReference("sample", sampleMap.get(sid));
				item.setAttribute("call", call);
				store(item);
			}

			count++;
			if (count % 5000 == 0) {
				System.out.println(String.format("LOG: %d lines were processed.", count));
			}
		}
	}

	private String platformRef = null;
	private String seriesRef = null;

	private String getPlatform() throws ObjectStoreException {
		if (platformRef == null) {
			Item item = createItem("MicroarrayPlatform");
			item.setAttribute("identifier", "GPL570");
			String title = "[HG-U133_Plus_2] Affymetrix Human Genome U133 Plus 2.0 Array";
			item.setAttribute("title", title);
			item.setReference("organism", getOrganism("9606"));
			store(item);
			platformRef = item.getIdentifier();
		}
		return platformRef;
	}
	
	private String getSeries() throws ObjectStoreException {
		if (seriesRef == null) {
			Item item = createItem("MicroarraySeries");
			item.setAttribute("identifier", "GSE7307");
			store(item);
			seriesRef = item.getIdentifier();
		}
		return seriesRef;
	}

	private String getProbSet(String probeSetId) throws ObjectStoreException {
		String ret = probeSetMap.get(probeSetId);
		if (ret == null) {
			Item item = createItem("ProbeSet");
			item.setAttribute("primaryIdentifier", probeSetId);
			store(item);
			ret = item.getIdentifier();
			probeSetMap.put(probeSetId, ret);
		}
		return ret;
	}

	private String getTissue(String tissue, String organ, String category) throws ObjectStoreException {
		String ret = tissueMap.get(tissue);
		if (ret == null) {
			Item item = createItem("HbiTissue");
			item.setAttribute("identifier", tissue);
			item.setAttribute("name", tissue);
			item.setAttribute("organ", organ);
			item.setAttribute("category", category);
			store(item);
			ret = item.getIdentifier();
			tissueMap.put(tissue, ret);
		}
		return ret;
	}
	
	private void readSampleInfoFile() throws Exception {
		System.out.println("...Read the sampleInfoFile.");
		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(new FileReader(sampleInfoFile));
		while (iterator.hasNext()) {
			String[] cols = iterator.next();
			String sampleId = cols[1];
			String tissue = cols[2];
			String organ = cols[6];
			String category = cols[7];
			
			Item item = createItem("MicroarraySample");
			item.setAttribute("identifier", sampleId);
			item.addToCollection("series", getSeries());
			String tissueRef = getTissue(tissue, organ, category);
			item.addToCollection("tissues", tissueRef);
			item.setReference("platform", getPlatform());
			store(item);
			
			sampleMap.put(sampleId, item.getIdentifier());
			sampleTissueMap.put(sampleId, tissueRef);
		}
		System.out.println("...Done.");
	}
}
