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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import nu.xom.*;
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
    private File mrStyFile;

    //
    private static final String DATASET_TITLE = "who-trial";
    private static final String DATA_SOURCE_NAME = "who-trial";

    private static final String DATA_TYPE_DISEASE_OR_SYNDROME = "B2.2.1.2.1";

    // key is string of source vocabularies, value is CUI
    private Map<String, String> mrConsoMap = new HashMap<String, String>();
    // key is CUI, value is reference to DiseaseTerm item
//    private Map<String, Item> diseaseTermMap = new HashMap<String, Item>();
    // key is CUI, value is reference to UmlsDisease item
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

    private static Map<String, String> propertyNames = new HashMap<String, String>();
    private static Map<String, String> whoTrial2XmlPropertyNames = new HashMap<String, String>();
    private static int STRING_LIMIT = 10000;

    private static final String WHO_TRIAL2_URL = "https://apps.who.int/trialsearch/Trial2.aspx?TrialID=%s";

    static {
        Map<String, String> p = new HashMap<>();
        propertyNames.put("name", "Main ID");
        propertyNames.put("title", "Public title");
        propertyNames.put("scientificTitle", "Scientific title");
        propertyNames.put("studyType", "Study type");
        propertyNames.put("recruitmentStatus", "Recruitment status");
        propertyNames.put("register", "Register");
        propertyNames.put("primarySponsor", "Primary sponsor");
        propertyNames.put("phase", "Phase");
        propertyNames.put("firstEnrolmentDate", "Date of first enrolment");
        propertyNames.put("registrationDate", "Date of registration");
        propertyNames.put("lastRefreshed", "Date of registration");
        propertyNames.put("targetSampleSize", "Target sample size");
        propertyNames.put("originalUrl", "URL");
        propertyNames.put("url", "url");
        propertyNames.put("interventions", "interventions");
        propertyNames.put("countries", "countries");
        propertyNames.put("primaryOutcome", "primary_outcome");
        propertyNames.put("secondaryOutcome", "secondary_outcome");
        propertyNames.put("result", "result");
    }

    static {
        whoTrial2XmlPropertyNames.put("name", "TrialID");
        whoTrial2XmlPropertyNames.put("title", "Public_title");
        whoTrial2XmlPropertyNames.put("scientificTitle", "Scientific_title");
        whoTrial2XmlPropertyNames.put("studyType", "Study_type");
        whoTrial2XmlPropertyNames.put("recruitmentStatus", "Recruitment_Status");
        whoTrial2XmlPropertyNames.put("register", "Source_Register");
        whoTrial2XmlPropertyNames.put("primarySponsor", "Primary_sponsor");
        whoTrial2XmlPropertyNames.put("phase", "Phase");
        whoTrial2XmlPropertyNames.put("firstEnrolmentDate", "Date_enrollement");
        whoTrial2XmlPropertyNames.put("registrationDate", "Date_registration");
        whoTrial2XmlPropertyNames.put("lastRefreshed", "Last_Refreshed_on");
        whoTrial2XmlPropertyNames.put("targetSampleSize", "Target_size");
        whoTrial2XmlPropertyNames.put("originalUrl", "web_address");
        whoTrial2XmlPropertyNames.put("interventions", "Intervention");
        whoTrial2XmlPropertyNames.put("countries", "Countries");
        whoTrial2XmlPropertyNames.put("primaryOutcome", "Primary_outcome");
        whoTrial2XmlPropertyNames.put("secondaryOutcome", "Secondary_outcome");
    }

    private static String toString(Object obj) {
        if (obj == null) {
            return null;
        } else if (obj instanceof String) {
            return (String) obj;
        } else if (obj instanceof JSONArray) {
            JSONArray array = (JSONArray) obj;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) {
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
        propertyNames.forEach((key, name) -> {
            String obj = null;
            if (main.has(name)) {
                obj = toString(main.get(name));
            } else if (jsonObject.has(name)) {
                obj = toString(jsonObject.get(name));
            }
            if (obj != null && !obj.isEmpty()) {
                if (obj.length() > STRING_LIMIT) {
                    LOG.warn("too large string at " + main.get("Main ID") + ", " + name + "= " + obj);
                }
                whoTrial.setAttribute(key, obj);
            }
        });
        JSONArray diseaseNames = jsonObject.getJSONArray("disease");
        if (diseaseNames != null && diseaseNames.length() > 0) {
            for (int i = 0; i < diseaseNames.length(); i++) {
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
        if (cui == null) {
            return null;
        }
        Item item = umlsDiseaseMap.get(cui);
        if (item == null) {

            item = createItem("UmlsDisease");
            item.setAttribute("identifier", cui);
            store(item);
            umlsDiseaseMap.put(cui, item);
        }
        return item;

    }

    /**
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        LOG.warn("[test]whoTrial start!");
        Document doc;
        Element rootElement;
        try {
            Builder parser = new Builder();

            doc = parser.build(reader);
            rootElement = doc.getRootElement();
            if(null == rootElement) {
                LOG.warn("[test]rootElements is null.");
                return;
            }
        } catch (ParsingException e) {
            LOG.warn("[test]cannot parsing this file.");
            return;
        }


        Elements trialElements = rootElement.getChildElements("Trial");
        if(null == trialElements) {
            LOG.warn("[test]trialElements is null.");
            return;
        }
        for (int i = 0; i < trialElements.size(); i++) {
            Item whoTrial = createItem("ClinicalTrial");
            Element trial = trialElements.get(i);

            whoTrial2XmlPropertyNames.forEach((key, name) -> {
                Element child = trial.getFirstChildElement(name);
                if(child == null) {
                    LOG.warn("*****[test]Nothing Element : " + name);
                    return;
                }
                String elementValue = child.getValue().trim();
                if(elementValue != null && !elementValue.isEmpty()) {
                    if(elementValue.length() > STRING_LIMIT) {
                        LOG.warn("too large string at " + trial.getFirstChildElement("TrialID") + ", " + name + "= " + elementValue);
                    }
                    LOG.warn("[test]key : " + key + ", elementValue : " + elementValue);
                    whoTrial.setAttribute(key, elementValue);

                    if(name.equals("TrialID")) {
                        String url = String.format(WHO_TRIAL2_URL,elementValue);
                        whoTrial.setAttribute("url", url);
                    }
                }
            });
            Element conditionElement = trial.getFirstChildElement("Condition");
            String condition = "";
            if(conditionElement != null) {
                condition = conditionElement.getValue();
            }
            LOG.warn("[test]condition = " + condition);
            try {
                store(whoTrial);
            } catch (ObjectStoreException e) {
                LOG.warn("Cannot sore who trials", e);
            }
        }

        /**
         * Processing MRSTY.RRF file to collect UMLS's source
         */
        Iterator<String[]> mrStyIterator = getMrStyIterator();
        mrStyIterator.next(); // Skip header
        HashSet<String> cuiSet = new HashSet<>();
        while( mrStyIterator.hasNext() ) {

            String[] mrStyRow = mrStyIterator.next();
            String cui = mrStyRow[0];
            String str = mrStyRow[2];
            if(!str.startsWith(DATA_TYPE_DISEASE_OR_SYNDROME)) {
                continue;
            }
            cuiSet.add(cui);
        }

        /**
         * Processing MRCONSO.RRF file to collect UMLS's source vocabularies and CUIs
         */
        Iterator<String[]> mrConsoIterator = getMrConsoIterator();
        mrConsoIterator.next(); // Skip header
        while (mrConsoIterator.hasNext()) {

            String[] mrConsoRow = mrConsoIterator.next();
            String cui = mrConsoRow[0];
            if(!cuiSet.contains(cui)) {
                continue;
            }
            String str = mrConsoRow[14];
            mrConsoMap.put(str.toLowerCase(), cui);

        }

        try (BufferedReader br = new BufferedReader(reader)) {
            br.lines().forEach(line -> storeTrial(line));
            LOG.warn("umlsDisease add total : " + umlsDiseaseMap.size());
        }
    }

    private Iterator<String[]> getMrConsoIterator() throws IOException {
        return FormattedTextParser.parseDelimitedReader(new FileReader(this.mrConsoFile), '|');
    }

    public void setMrConsoFile(File mrConsoFile) {
        this.mrConsoFile = mrConsoFile;
    }

    private Iterator<String[]> getMrStyIterator() throws IOException {
        // delimiter '|'
        return FormattedTextParser.parseDelimitedReader(new FileReader( this.mrStyFile ), '|' );
    }

    public void setMrStyFile( File mrStyFile ) {
        this.mrStyFile = mrStyFile;
    }

}
