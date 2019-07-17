package org.intermine.bio.dataconversion;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.ParsingException;

public class TrialXMLParser implements TrialParser {
	private static final Logger LOG = LogManager.getLogger(TrialXMLParser.class);

	private static Map<String, String> whoTrial2XmlPropertyNames = new HashMap<String, String>();
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
	private static final String WHO_TRIAL2_URL = "https://apps.who.int/trialsearch/Trial2.aspx?TrialID=%s";
	public static String[] getkeys() {
		return whoTrial2XmlPropertyNames.keySet().toArray(new String[0]);
	}
	private static int STRING_LIMIT = 10000;
	private Reader reader;

	public TrialXMLParser(Reader reader) throws IOException {
		this.reader = reader;
		init();
	}
	private  Elements trialElements;
	private int trialIndex;
	private void init() throws IOException {
		Document doc;
		Element rootElement;
		trialElements = null;
		try {
			Builder parser = new Builder();
			doc = parser.build(reader);
			rootElement = doc.getRootElement();
			if(null == rootElement) {
				LOG.warn("RootElements is null. read next file.");
				return;
			}
		} catch (ParsingException e) {
			LOG.warn("Cannot XML parsing this file. read next file.");
			return;
		}

		trialElements = rootElement.getChildElements("Trial");
		trialIndex = 0;
	}
	@Override
	public Map<String, String> parse() throws IOException {
		if(trialElements==null) {
			init();
		}
		if(trialElements!=null && trialIndex < trialElements.size()) {
			return parse(trialElements.get(trialIndex++));
		}
		return null;
	}
	public Map<String, String> parse(Element trial) {
		HashMap<String, String> map = new HashMap<String, String>();
				;

		whoTrial2XmlPropertyNames.forEach((key, name) -> {
			Element child = trial.getFirstChildElement(name);
			if(child == null) {
				LOG.warn("*****[test]Nothing Element : " + name);
				return;
			}
			String elementValue = child.getValue().trim();
			if(elementValue != null && !elementValue.isEmpty()) {
				if(elementValue.length() > STRING_LIMIT) {
					LOG.warn("too large string at " + trial.getFirstChildElement("TrialID").getValue() + ", " + name + "= " + elementValue);
				}
				LOG.warn("[test]key : " + key + ", elementValue : " + elementValue);
				map.put(key, elementValue);

				if(name.equals("TrialID")) {
					String url = String.format(WHO_TRIAL2_URL,elementValue);
					map.put("url", url);
				}
			}
		});

		// add disease.
		Element conditionElement = trial.getFirstChildElement("Condition");
		if(conditionElement != null) {
			map.put("condition", conditionElement.getValue());
		}

		return map;
	}
}
