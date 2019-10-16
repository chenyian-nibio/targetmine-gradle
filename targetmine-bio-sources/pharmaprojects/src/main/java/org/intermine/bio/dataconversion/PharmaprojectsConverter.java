package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2019 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.io.IOException;

import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.xml.full.Item;
import org.json.JSONArray;
import org.json.JSONObject;


/**
 * 
 * @author
 */
public class PharmaprojectsConverter extends BioFileConverter
{
    //
    private static final String DATASET_TITLE = "Add DataSet.title here";
    private static final String DATA_SOURCE_NAME = "Add DataSource.name here";

    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public PharmaprojectsConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }
	private static Map<String, JsonToStr> propertyNames = new HashMap<String, JsonToStr>();

	static {
		propertyNames.put("identifier",new JsonToStr("drugPrimaryName"));
		propertyNames.put("overview", new JsonToStr("overview"));
		propertyNames.put("origin", new JsonToStr("origin"));
		propertyNames.put("icd9", new JsonToStr("drugIcd9","${icd9Id} ${name}"));
		propertyNames.put("icd10", new JsonToStr("drugIcd10","${icd10Id} ${name}"));
		propertyNames.put("preClinical", new JsonToStr("preClinical"));
		propertyNames.put("phaseI", new JsonToStr("phaseI"));
		propertyNames.put("phaseII", new JsonToStr("phaseII"));
		propertyNames.put("phaseIII", new JsonToStr("phaseIII"));
		propertyNames.put("mechanismsOfAction", new JsonToStr("mechanismsOfAction"));
		propertyNames.put("originator", new JsonToStr("originatorName"));
		propertyNames.put("therapeuticClasses", new JsonToStr("therapeuticClasses","${therapeuticClassName}(${therapeuticClassStatus})"));
		propertyNames.put("pharmacokinetics", new JsonToStr("pharmacokinetics","${model} ${parameter} ${unit}"));
		propertyNames.put("patents", new JsonToStr("patents","${patentNumber}"));
		propertyNames.put("marketing", new JsonToStr("marketing"));
		propertyNames.put("recordUrl",new JsonToStr( "recordUrl"));
	}

	public void createPharmaProject(JSONObject item) throws ObjectStoreException {
		Item project = createItem("PharmaProject");
		for (Entry<String, JsonToStr> entry : propertyNames.entrySet()) {
			String opt = entry.getValue().toString(item);
			if(opt!=null && opt.length() > 0) {
				project.setAttribute(entry.getKey(), opt);
			}
		}
		store(project);
	}
	private String readAll(Reader reader) throws IOException {
		StringBuilder sb = new StringBuilder();
		char[] buff = new char[4096];
		int len = 0;
		while((len = reader.read(buff))>0) {
			sb.append(buff, 0, len);
		}
		return sb.toString();
	}

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        JSONObject jsonObject = new JSONObject(readAll(reader));
        JSONArray jsonArray = jsonObject.getJSONArray("items");
        for (int i = 0; i < jsonArray.length(); i++) {
        	JSONObject item = jsonArray.getJSONObject(i);
        	createPharmaProject(item);
		}
    }
}
