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
    private Map<String, String> diseaseTermMap = new HashMap<String, String>();

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
        propertyNames.put("originalURL","URL");
        propertyNames.put("url","url");
        propertyNames.put("interventions","interventions");
        propertyNames.put("countries","countries");
        propertyNames.put("primary_outcome","primary_outcome");
        propertyNames.put("secondary_outcome","secondary_outcome");
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
        Item whoTrial = createItem("WhoTrial");
        propertyNames.forEach((key,name)->{
            if(main.has(name)){
                whoTrial.setAttribute(key,toString(main.get(name)));
            }else if(jsonObject.has(name)){
                whoTrial.setAttribute(key,toString(jsonObject.get(name)));
            }
        });
        String diseaseName = jsonObject.getString("disease");
        whoTrial.setAttribute("condition", diseaseName);
        String cui = mrConsoMap.get(diseaseName.toLowerCase());
        try {
            if (null != cui) {
                whoTrial.setAttribute("diseaseTerms", getDiseaseTerm(cui, diseaseName));
            }
            store(whoTrial);
        } catch (ObjectStoreException e) {
            LOG.warn("Cannot sore who trials", e);
        }

    }

    private String getDiseaseTerm(String cui, String diseaseName) throws ObjectStoreException {

        String diseaseTermRef = diseaseTermMap.get(cui);
        if (diseaseTermRef == null) {

            Item item = createItem("DiseaseTerm");
            item.setAttribute("identifier", cui);
            item.setAttribute("name", diseaseName);
            item.setAttribute("description", diseaseName);
            store(item);
            String ref = item.getIdentifier();
            diseaseTermMap.put(cui, ref);
        }
        return diseaseTermMap.get(cui);

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
