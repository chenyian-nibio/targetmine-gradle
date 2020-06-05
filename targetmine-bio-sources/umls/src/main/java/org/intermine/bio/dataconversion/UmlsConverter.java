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
import java.io.File;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.xml.full.Item;


/**
 *
 * @author
 */
public class UmlsConverter extends BioFileConverter
{
	private static final Logger LOG = Logger.getLogger(UmlsConverter.class);

	//
	private static final String DATASET_TITLE = "2018AB";
	private static final String DATA_SOURCE_NAME = "UMLS";

	private File mrStyFile;

	/**
	 * Constructor
	 * @param writer the ItemWriter used to handle the resultant items
	 * @param model the Model
	 */
	public UmlsConverter(ItemWriter writer, Model model) {
		super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
	}
	private IdSetLoader diseaseTermIdSet;
	/**
	 *
	 *
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		diseaseTermIdSet = new IdSetLoader(osAlias, "DiseaseTerm", "identifier");
		diseaseTermIdSet.loadIds();
		try(UMLSParser parser = new UMLSParser(reader, mrStyFile,UMLSParser.DATA_TYPES)){
			UMLS umls = null;
			HashSet<String> keySet = new HashSet<>();
			Item diseaseConcept = null;
			String prevIdentifier = null;
			while((umls = parser.getNext())!=null) {
				String identifier = umls.getIdentifier();
				if(diseaseConcept == null || !identifier.equals(prevIdentifier)) {
					if(diseaseConcept!=null) {
						store(diseaseConcept);
					}
					diseaseConcept = createItem("DiseaseConcept");
					diseaseConcept.setAttribute("identifier",identifier);
					String name = umls.getName();
					diseaseConcept.setAttribute("name",name);
					
					Item umlsTerm = createItem("UMLSTerm");
					umlsTerm.setAttribute("identifier","UMLS:" + identifier);
					umlsTerm.setAttribute("name",name);
					umlsTerm.setReference("ontology", getOntology("UMLS"));

					diseaseConcept.addToCollection("terms", umlsTerm);
					store(umlsTerm);
					if(diseaseTermIdSet.hasId(identifier)) {
						String medgenIdentifier = getOrCreateItem("DiseaseTerm", identifier);
						diseaseConcept.addToCollection("terms", medgenIdentifier);
					}
				}
				if("MSH".equals(umls.getDbType())){
					String meshId = umls.getDbId();
					String key = identifier+":"+meshId;
					if(keySet.contains(key)) {
						continue;
					}
					String meshIdentifier = getOrCreateItem("MeshTerm", meshId);
					diseaseConcept.addToCollection("terms", meshIdentifier);
				}
				prevIdentifier = identifier;
			}
			if(diseaseConcept!=null) {
				store(diseaseConcept);
			}
		}
	}
	private HashMap<String,HashMap<String,String>> itemSet = new HashMap<String, HashMap<String,String>>();

	private String getOrCreateItem(String dbName,String identifier) throws ObjectStoreException {
		HashMap<String, String> hashMap = itemSet.get(dbName);
		if(hashMap==null) {
			hashMap = new HashMap<String, String>();
			itemSet.put(dbName, hashMap);
		}
		String termIdentifier = hashMap.get(identifier);
		if(termIdentifier==null) {
			Item item = createItem(dbName);
			item.setAttribute("identifier", identifier);
			store(item);
			termIdentifier = item.getIdentifier();
			hashMap.put(identifier, termIdentifier);
		}
		return termIdentifier;
	}
	private String osAlias = null;

	public void setOsAlias(String osAlias) {
		this.osAlias = osAlias;
	}

	public void setMrStyFile( File mrStyFile ) {
		this.mrStyFile = mrStyFile;
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
