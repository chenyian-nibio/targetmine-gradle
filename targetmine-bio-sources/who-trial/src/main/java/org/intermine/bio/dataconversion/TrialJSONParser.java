package org.intermine.bio.dataconversion;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

public class TrialJSONParser implements TrialParser{
    private static final Logger LOG = LogManager.getLogger(TrialJSONParser.class);

	private static Map<String, String> propertyNames = new HashMap<String, String>();
	private static int STRING_LIMIT = 10000;
	static {
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
		propertyNames.put("criteria", "criteria");
	}
	private BufferedReader reader;

	public TrialJSONParser(Reader reader) throws IOException {
		this.reader = new BufferedReader(reader);
	}

	@Override
	public Map<String, String> parse() throws IOException {
		String line = null;
		if(reader == null || (line = reader.readLine())==null) {
			return null;
		}
        JSONObject jsonObject = new JSONObject(line);
        JSONObject main = jsonObject.getJSONObject("main");
        Map<String, String> whoTrial = new HashMap<String, String>();
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
                whoTrial.put(key, obj);
            }
        });
        JSONArray diseaseNames = jsonObject.getJSONArray("disease");
        StringBuilder sb = new StringBuilder();
        if (diseaseNames != null && diseaseNames.length() > 0) {
            for (int i = 0; i < diseaseNames.length(); i++) {
                String diseaseName = diseaseNames.getString(i);
                if(sb.length()>0) {
                	sb.append("<br>");
                }
                sb.append(diseaseName);
            }
            whoTrial.put("condition", sb.toString());
        }
		return whoTrial;
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
		} else {
			// no need to output logs if the value is simply null
			if (obj != JSONObject.NULL) {
				LOG.warn("unexpected type " + obj.getClass().getName() + ", " + obj.toString());
			}
		}
		return null;
	}

}
