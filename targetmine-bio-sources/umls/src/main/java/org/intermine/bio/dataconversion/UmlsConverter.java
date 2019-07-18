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
import java.util.Iterator;
import java.util.Set;

import org.apache.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreFactory;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
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

	/**
	 *
	 *
	 * {@inheritDoc}
	 */
	public void process(Reader reader) throws Exception {
		getDiseaseTermIds();
		try(UMLSParser parser = new UMLSParser(reader, mrStyFile,UMLSParser.DATA_TYPES)){
			UMLS umls = null;
			HashMap<String, Item> umlsMap = new HashMap<>();
			HashSet<String> keySet = new HashSet<>();
			Item prevUmls = null;
			while((umls = parser.getNext())!=null) {
				String identifier = umls.getIdentifier();
				if(prevUmls!=null && prevUmls.getIdentifier().equals(identifier)) {
					store(prevUmls);
				}
				Item umlsDisease = umlsMap.get(identifier);
				if(umlsDisease==null){
					umlsDisease = createItem("UMLSDisease");
					umlsDisease.setAttribute("identifier",identifier);
					String name = umls.getName();
					umlsDisease.setAttribute("name",name);
					if(diseaseTermIdSet.contains(identifier)) {
						Item medgen = getOrCreateItem("DiseaseTerm", identifier);
						umlsDisease.setReference("medgen", medgen);
					}
					store(umlsDisease);
					umlsMap.put(identifier,umlsDisease);
				}
				if("MSH".equals(umls.getDbType())){
					String meshId = umls.getDbId();
					String key = identifier+":"+meshId;
					if(keySet.contains(key)) {
						continue;
					}
					Item mesh = getOrCreateItem("MeshTerm", meshId);
					umlsDisease.addToCollection("meshes", mesh);
				}
				prevUmls = umlsDisease;
			}
			if(prevUmls!=null) {
				store(prevUmls);
			}
		}
	}
	private HashMap<String,HashMap<String,Item>> itemSet = new HashMap<String, HashMap<String,Item>>();

	private Item getOrCreateItem(String dbName,String identifier) throws ObjectStoreException {
		HashMap<String, Item> hashMap = itemSet.get(dbName);
		if(hashMap==null) {
			hashMap = new HashMap<String, Item>();
			itemSet.put(dbName, hashMap);
		}
		Item item = hashMap.get(identifier);
		if(item==null) {
			item = createItem(dbName);
			item.setAttribute("identifier", identifier);
			store(item);
			hashMap.put(identifier, item);
		}
		return item;
	}
	private String osAlias = null;

	public void setOsAlias(String osAlias) {
		this.osAlias = osAlias;
	}

	Set<String> diseaseTermIdSet = new HashSet<String>();

	@SuppressWarnings("unchecked")
	private void getDiseaseTermIds() throws Exception {
		LOG.info("Start loading diseaseterm id");
		ObjectStore os = ObjectStoreFactory.getObjectStore(osAlias);

		Query q = new Query();
		QueryClass qcDiseaseTerm = new QueryClass(os.getModel().getClassDescriptorByName("DiseaseTerm").getType());

		QueryField qfIdentifier = new QueryField(qcDiseaseTerm, "identifier");

		q.addFrom(qcDiseaseTerm);
		q.addToSelect(qfIdentifier);

		Results results = os.execute(q);
		Iterator<Object> iterator = results.iterator();
		while (iterator.hasNext()) {
			ResultsRow<String> rr = (ResultsRow<String>) iterator.next();
			diseaseTermIdSet.add(rr.get(0));
		}
		LOG.info("loaded "+ diseaseTermIdSet.size()+" diseaseterm id " );
	}

	public void setMrStyFile( File mrStyFile ) {
		this.mrStyFile = mrStyFile;
	}
}
