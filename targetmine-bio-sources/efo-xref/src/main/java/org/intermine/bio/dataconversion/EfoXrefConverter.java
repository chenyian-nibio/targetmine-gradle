package org.intermine.bio.dataconversion;

import java.io.BufferedReader;
import java.io.File;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.xml.full.Item;


/**
 * used for extract cross reference from efo terms to mesh terms and do terms
 * 
 * @author chenyian
 */
public class EfoXrefConverter extends BioFileConverter
{
	private static final Logger LOG = Logger.getLogger(EfoXrefConverter.class);
	//
	//    private static final String DATASET_TITLE = "Add DataSet.title here";
	//    private static final String DATA_SOURCE_NAME = "Add DataSource.name here";
	private File mrConsoFile;

	public void setMrConsoFile( File mrConsoFile ) {
		this.mrConsoFile = mrConsoFile;
	}
	private File mrStyFile;
	public void setMrStyFile(File mrStyFile) {
		this.mrStyFile = mrStyFile;
	}
	/**
	 * Constructor
	 * @param writer the ItemWriter used to handle the resultant items
	 * @param model the Model
	 */
	public EfoXrefConverter(ItemWriter writer, Model model) {
		super(writer, model);
	}

	private static Pattern synonymPattern = Pattern.compile("synonym: \\\"([^\\\"]+)\\\"");
	/**
	 * 
	 *
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		BufferedReader in = new BufferedReader(reader);
		String line;
		boolean isTerm = false;
		String identifier = null;
		boolean isObsolete = false;
		diseaseEfoSet.add("EFO:0000408");
		UMLSResolver resolver = new UMLSResolver(mrConsoFile,mrStyFile);
		Set<String> meshIdSet = new HashSet<String>();
		Set<String> doIdSet = new HashSet<String>();
		Set<String> cuiIdSet = new HashSet<String>();
		String cui = null;
		while ((line = in.readLine()) != null) {
			if (line.equals("[Term]")) {
				isTerm = true;
			} else if (line.startsWith("id:")) {
				identifier = line.substring(4);
			} else if (line.startsWith("name: ")) {
				String name = line.substring("name: ".length()).trim();
				cui = resolver.getIdentifier(name);
				if(cui!=null) {
					cuiIdSet.add(cui);
					System.out.println("UMLSLINK\t" + identifier +"\t" + cui +"\t"+name);
				}
			} else if (line.startsWith("synonym: ")) {
				Matcher matcher = synonymPattern.matcher(line);
				if(cui == null && matcher.lookingAt()) {
					String name = matcher.group(1);
					cui = resolver.getIdentifier(name);
					if(cui!=null){
						cuiIdSet.add(cui);
						System.out.println("UMLSLINK\t" + identifier +"\t" + cui +"\t"+name);
					}
				}
			} else if (line.startsWith("property_value: http://www.ebi.ac.uk/efo/MSH_definition_citation")) {
				String meshIdentifier = line.substring(line.indexOf("MSH:") + 4, line.indexOf("MSH:") + 11);
				if (!"".equals(meshIdentifier)) {
					meshIdSet.add(meshIdentifier);
				}
			} else if (line.startsWith("property_value: http://www.ebi.ac.uk/efo/DOID_definition_citation")
					&& line.contains("DOID:")) {
				String doIdentifier = line.substring(line.indexOf("DOID:"), line.indexOf("xsd:") - 1);
				if (!"".equals(doIdentifier)) {
					doIdSet.add(doIdentifier);
				}
			} else if ("is_obsolete: true".equals(line.trim())) {
				isObsolete = true;
			} else if (line.startsWith("is_a:")){
				String parentId = line.substring(6, line.indexOf('!')-1);
				if (diseaseEfoSet.contains(parentId)){
					diseaseEfoSet.add(identifier);
				}
			} else if ("".equals(line.trim())) {
				if (isTerm && !isObsolete) {
					Item efoTerm = createItem("EFOTerm");
					efoTerm.setAttribute("identifier", identifier);
					efoTerm.setReference("ontology", getOntology("EFO"));
					if(identifier.startsWith("EFO:")) {
						for (String cuiId:cuiIdSet){
							efoTerm.addToCollection("diseaseConcepts", getUMLSDisease(cuiId));
						}
					}
					for (String meshIdentifier : meshIdSet) {
						efoTerm.addToCollection("crossReferences", getMeshTerm(meshIdentifier));
					}
					for (String doIdentifier : doIdSet) {
						efoTerm.addToCollection("crossReferences", getDoTerm(doIdentifier));
					}
					store(efoTerm);
				}
				isTerm = false;
				isObsolete = false;
				cui = null;
				meshIdSet = new HashSet<String>();
				doIdSet = new HashSet<String>();
				cuiIdSet = new HashSet<String>();
			}

		}
	}
	private Set<String> diseaseEfoSet = new HashSet<String>();

	private Map<String, Item> umlsMap = new HashMap<String, Item>();
	private Item getUMLSDisease(String cui) throws ObjectStoreException {
		Item item = umlsMap.get(cui);
		if (item == null) {
			item = createItem("DiseaseConcept");
			item.setAttribute("identifier", cui);
			store(item);
			umlsMap.put(cui, item);
		}
		return item;
	}

	private Map<String, String> meshTermMap = new HashMap<String, String>();
	private String getMeshTerm(String meshIdentifier) throws ObjectStoreException {
		String ret = meshTermMap.get(meshIdentifier);
		if (ret == null) {
			Item item = createItem("MeshTerm");
			item.setAttribute("identifier", meshIdentifier);
			item.setReference("ontology", getOntology("MeSH"));
			store(item);
			ret = item.getIdentifier();
			meshTermMap.put(meshIdentifier, ret);
		}
		return ret;
	}

	private Map<String, String> doTermMap = new HashMap<String, String>();
	private String getDoTerm(String identifier) throws ObjectStoreException {
		String ret = doTermMap.get(identifier);
		if (ret == null) {
			Item item = createItem("DOTerm");
			item.setAttribute("identifier", identifier);
			item.setReference("ontology", getOntology("DO"));
			store(item);
			ret = item.getIdentifier();
			doTermMap.put(identifier, ret);
		}
		return ret;
	}

	private Map<String, String> ontologyMap = new HashMap<String, String>();
	private String getOntology(String name) throws ObjectStoreException {
		String ret = ontologyMap.get(name);
		if (ret == null) {
			Item item = createItem("Ontology");
			item.setAttribute("name", name);
			store(item);
			ret = item.getIdentifier();
			ontologyMap.put(name, ret);
		}
		return ret;
	}

}
