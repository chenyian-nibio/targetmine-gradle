package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2018 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;
import org.json.JSONArray;
import org.json.JSONObject;


/**
 * @author
 */
public class WhoTrialConverter extends BioFileConverter {
    private static final Logger LOG = LogManager.getLogger(WhoTrialConverter.class);
    private File mrConsoFile;

    //
    private static final String DATASET_TITLE = "who-trial";
    private static final String DATA_SOURCE_NAME = "who-trial";
    // key is string of source vocabularies, value is CUI
    private Map<String, String> mrConsoMap = new HashMap<String, String>();
    // key is CUI, value is reference to DiseaseTerm item
//    private Map<String, Item> diseaseTermMap = new HashMap<String, Item>();
    private Map<String, Item> umlsDiseaseMap = new HashMap<String, Item>();

    /**
     * Constructor
     *
     * @param writer the ItemWriter used to handle the resultant items
     * @param model  the Model
     */
    public WhoTrialConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }
    private static Map<String,String> propertyNames = new HashMap<String,String>();
    private static int STRING_LIMIT = 10000;
    static {
        Map<String,String> p = new HashMap<>();
        propertyNames.put("name","Main ID");
        propertyNames.put("title","Public title");
        propertyNames.put("scientificTitle","Scientific title");
        propertyNames.put("studyType","Study type");
        propertyNames.put("recruitmentStatus","Recruitment status");
        propertyNames.put("register","Register");
        propertyNames.put("primarySponsor","Primary sponsor");
        propertyNames.put("phase","Phase");
        propertyNames.put("firstEnrolmentDate","Date of first enrolment");
        propertyNames.put("registrationDate","Date of registration");
        propertyNames.put("lastRefreshed","Date of registration");
        propertyNames.put("targetSampleSize","Target sample size");
        propertyNames.put("originalUrl","URL");
        propertyNames.put("url","url");
        propertyNames.put("interventions","interventions");
        propertyNames.put("countries","countries");
        propertyNames.put("primaryOutcome","primary_outcome");
        propertyNames.put("secondaryOutcome","secondary_outcome");
        propertyNames.put("result","result");
    }
    private static String toString(Object obj){
        if(obj==null){
            return null;
        }else if(obj instanceof String){
            return (String) obj;
        }else if(obj instanceof JSONArray){
            JSONArray array = (JSONArray) obj;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                if(i>0){
                    sb.append(" ");
                }
                sb.append(array.get(i).toString());
            }
            return sb.toString();
        }
        LOG.warn("unexpected type " + obj.getClass().getName());
        return null;
    }
    private void storeTrial(String line) {
        JSONObject jsonObject = new JSONObject(line);
        JSONObject main = jsonObject.getJSONObject("main");
        Item whoTrial = createItem("ClinicalTrial");
        propertyNames.forEach((key,name)->{
	    String obj = null;
            if(main.has(name)){
		obj = toString(main.get(name));
            }else if(jsonObject.has(name)){
	        obj = toString(jsonObject.get(name));
            }
	    if(obj!=null && !obj.isEmpty() ){
		if(obj.length() > STRING_LIMIT){
			LOG.warn("too large string at " +main.get("Main ID") +", "+name+"= "+obj);
		}
                whoTrial.setAttribute(key,obj);
	    }
        });
        JSONArray diseaseNames = jsonObject.getJSONArray("disease");
	if(diseaseNames != null && diseaseNames.length() > 0){
	    for(int i=0;i<diseaseNames.length();i++){
		String diseaseName = diseaseNames.getString(i);
//		Item trialTo = createItem("TrialToDisease");
        Item trialTo = createItem("TrialToUmlsDisease");
		diseaseName = diseaseName.trim();
        	trialTo.setAttribute("diseaseName", diseaseName);
		try {
//		    Item disease = getDiseaseTerm(diseaseName);
            Item umlsDisease = getUmlsDisease(diseaseName);
//		    if (null != disease) {
//			trialTo.setReference("disease", disease);
            if (null != umlsDisease) {
                trialTo.setReference("umls", umlsDisease);
			    trialTo.setReference("trial", whoTrial);
		    }
		    store(trialTo);
		} catch (ObjectStoreException e) {
		    LOG.warn("Cannot sore who trials", e);
		}
	    }

	}
	try {
	    store(whoTrial);
	} catch (ObjectStoreException e) {
	    LOG.warn("Cannot sore who trials", e);
	}

    }

//    private Item getDiseaseTerm(String diseaseName) throws ObjectStoreException {
//	String cui = mrConsoMap.get(diseaseName.toLowerCase());
//	if(cui == null){
//	    return null;
//	}
//        Item item = diseaseTermMap.get(cui);
//        if (item == null) {
//
//            item = createItem("DiseaseTerm");
//            item.setAttribute("identifier", cui);
//            item.setAttribute("name", diseaseName);
//            item.setAttribute("description", diseaseName);
//            store(item);
//            diseaseTermMap.put(cui, item);
//        }
//        return item;
//
//    }

    private Item getUmlsDisease(String umlsDiseaseName) throws ObjectStoreException {
        String cui = mrConsoMap.get(umlsDiseaseName.toLowerCase());
        if(cui == null){
            return null;
        }
        Item item = umlsDiseaseMap.get(cui);
        if (item == null) {

            item = createItem("UmlsDisease");
            item.setAttribute("identifier", cui);
            item.setAttribute("name", umlsDiseaseName);
            store(item);
            umlsDiseaseMap.put(cui, item);
        }
        return item;

    }

    /**
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        /**
         * Processing MRCONSO.RRF file to collect UMLS's source vocabularies and CUIs
         */
        Iterator<String[]> mrConsoIterator = getMrConsoIterator();
        mrConsoIterator.next(); // Skip header
        while (mrConsoIterator.hasNext()) {

            String[] mrConsoRow = mrConsoIterator.next();
            String cui = mrConsoRow[0];
            String str = mrConsoRow[14];
            mrConsoMap.put(str.toLowerCase(), cui);

        }

        try (BufferedReader br = new BufferedReader(reader)) {
            br.lines().forEach(line -> storeTrial(line));
        }
    }

    private Iterator<String[]> getMrConsoIterator() throws IOException {
        return FormattedTextParser.parseDelimitedReader(new FileReader(this.mrConsoFile), '|');
    }

    public void setMrConsoFile(File mrConsoFile) {
        this.mrConsoFile = mrConsoFile;
    }

}
