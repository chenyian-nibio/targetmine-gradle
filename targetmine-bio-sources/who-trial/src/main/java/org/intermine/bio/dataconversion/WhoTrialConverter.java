package org.intermine.bio.dataconversion;

import java.io.File;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.xml.full.Item;

/**
 * @author mss-uehara-san
 */
public class WhoTrialConverter extends BioFileConverter {
	private static final Logger LOG = LogManager.getLogger(WhoTrialConverter.class);
	private File mrConsoFile;
	private File mrStyFile;

	//
	private static final String DATASET_TITLE = "WHO ICTRP";
	private static final String DATA_SOURCE_NAME = "WHO";

	// key is CUI, value is reference to UmlsDisease item
	private Map<String, String> umlsTermMap = new HashMap<String, String>();

	/**
	 * Constructor
	 *
	 * @param writer the ItemWriter used to handle the resultant items
	 * @param model  the Model
	 */
	public WhoTrialConverter(ItemWriter writer, Model model) {
		super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
	}
	private static int STRING_LIMIT = 10000;
	private static int PRIMARY_KEY_STRING_LIMIT = 1000;

	private static final String WHO_TRIAL2_URL = "https://apps.who.int/trialsearch/Trial2.aspx?TrialID=%s";
	
	private Set<String> idSet = new HashSet<>();

	private void storeTrialElements(Map<String,String> trial) throws ObjectStoreException {
		String name = trial.get("name");
		if(idSet.contains(name) || name == null) {
			if(name ==null){
				System.out.println(trial);
			}
			return;
		}
		Item trialGroup = createItem("TrialGroup");
		trialGroup.setAttribute("identifier", name);
		store(trialGroup);
		Item whoTrial = createItem("WHOTrial");
		whoTrial.setReference("trialGroup", trialGroup);
		for (String key : TrialXMLParser.getkeys()) {
			String value = trial.get(key);
			if(value == null) {
				continue;
			}
			String elementValue = value.trim();
			if(elementValue != null && !elementValue.isEmpty()) {
				if(elementValue.length() > STRING_LIMIT) {
					LOG.warn("too large string at " + trial.get("Name") + ", " + key + "= " + elementValue);
				}
				// slightly pretty the contents
				elementValue = elementValue.replaceAll("^<br>", " ");
				elementValue = elementValue.replaceAll("[\n\r]+", " ");
				elementValue = elementValue.trim();

				whoTrial.setAttribute(key, elementValue);

			}

		}
		idSet.add(name);
		String url = String.format(WHO_TRIAL2_URL,name);
		whoTrial.setAttribute("url", url);

		String condition = trial.get("condition");
		if (condition != null) {
			Set<String> diseaseNameSet = convertConditionToDiseaseNameSet(condition);
			Set<String> umlsTerms = new HashSet<String>();
			for(String diseaseName : diseaseNameSet){
				if (diseaseName != null && diseaseName.length() > 0) {
					String umlsTerm = getUMLSTerm(diseaseName);
					if (umlsTerm != null) {
						umlsTerms.add(umlsTerm);
					}
				}
			}
			if(!umlsTerms.isEmpty()) {
				whoTrial.setCollection("umlsTerms", new ArrayList<String>(umlsTerms));
			}
		}
		store(whoTrial);
	}

	private Set<String> convertConditionToDiseaseNameSet(String condition) {
		String[] diseaseNames = condition.split("<[Bb][Rr]>|;");

		Set<String> diseaseNameSet = new HashSet<>();
		for(String diseaseName : diseaseNames) {
			diseaseName = diseaseName.trim();
			if(diseaseName != null && !diseaseName.isEmpty()) {
				if(diseaseName.length() > PRIMARY_KEY_STRING_LIMIT) {
					LOG.warn("diseaseName OVER LIMTT 1000, str = " + diseaseName);
					diseaseName = diseaseName.substring(0, PRIMARY_KEY_STRING_LIMIT);
				}
				diseaseNameSet.add(diseaseName);
			}
		}
		return diseaseNameSet;
	}
	
	private String getUMLSTerm(String diseaseName) throws ObjectStoreException {
		String cui = getCUI(diseaseName);
		if (cui == null) {
			return null;
		}
		String ret = umlsTermMap.get(cui);
		if (ret == null) {
			Item item = createItem("UMLSTerm");
			item.setAttribute("identifier", "UMLS:" + cui);
			store(item);
			ret = item.getIdentifier();
			umlsTermMap.put(cui, ret);
		}
		return ret;

	}
	private static Pattern meddraPattern = Pattern.compile("Term:\\s+(.*)\\s*");
	private String getCUI(String diseaseName) {
		String cui = resolver.getIdentifier(diseaseName);
		if (cui != null) {
			return cui;
		}
		if(diseaseName.startsWith("MedDRA")) {
			String[] lines = diseaseName.split("\n");
			for (String line : lines) {
				Matcher matcher = meddraPattern.matcher(line);
				if(matcher.matches()) {
					String term = matcher.group(1);
					cui = resolver.getIdentifier(term);
					return cui;
				}
			}
		}
		return null;
	}
	private UMLSResolver resolver;
	/**
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		// mrConsoMap input data only once.
		// because, if project.xml( src.data.dir.includes) include multi files , the process method will be called multiple times.
		if(resolver==null) {
			resolver = new UMLSResolver(mrConsoFile, mrStyFile);
		}

		LOG.warn("whoTrial2 start!");
		boolean isXML = getCurrentFile().getName().endsWith("xml");
		TrialParser parser = isXML?new TrialXMLParser(reader):new TrialJSONParser(reader);
		Map<String, String> trial = null;
		while((trial = parser.parse())!=null) {
			storeTrialElements(trial);
		}
	}
	public void setMrConsoFile(File mrConsoFile) {
		this.mrConsoFile = mrConsoFile;
	}

	public void setMrStyFile( File mrStyFile ) {
		this.mrStyFile = mrStyFile;
	}

	
	@Override
	public void close() throws Exception {
		super.close();
		System.out.println(String.format("Create %d UMLSTrems.", umlsTermMap.size()));
	}
}
