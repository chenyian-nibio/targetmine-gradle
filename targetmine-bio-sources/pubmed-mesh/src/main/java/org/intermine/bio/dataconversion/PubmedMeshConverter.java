package org.intermine.bio.dataconversion;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.model.bio.Publication;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreFactory;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;


/**
 * 
 * @author chenyian
 */
public class PubmedMeshConverter extends BioFileConverter
{
	private static final Logger LOG = LogManager.getLogger(PubmedMeshConverter.class);
	//
    private static final String DATASET_TITLE = "PubMed";
    private static final String DATA_SOURCE_NAME = "PubMed";

    private Set<String> pubMedIds;
    private Map<String, String> publicationMap = new HashMap<String, String>();
    
	private File updateFile;

	public void setUpdateFile(File updateFile) {
		this.updateFile = updateFile;
	}

	private String osAlias;

	public void setOsAlias(String osAlias) {
		this.osAlias = osAlias;
	}
	
    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public PubmedMeshConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    /**
     * 
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
    	if (pubMedIds == null) {
    		pubMedIds = getPubMedIds();
    		// update file contents should be read first
    		System.out.println("Processing the update file ...");
    		LOG.info("Processing the update file ...");
    		processMeshTxtFile(new FileReader(updateFile));
    	}
    	
		String fileName = getCurrentFile().getName();
		LOG.info("Processing the file " + fileName + " ...");
		
    	processMeshTxtFile(reader);
    }

	private void processMeshTxtFile(Reader reader) throws IOException, ObjectStoreException {
		Iterator<String[]> iterator = FormattedTextParser.parseTabDelimitedReader(reader);
    	while (iterator.hasNext()) {
    		String[] cols = iterator.next();
    		String pubMedId = cols[0];
    		if (publicationMap.get(pubMedId) != null) {
    			continue;
    		}
    		
    		if (pubMedIds.contains(pubMedId)) {
        		Item publication = createItem("Publication");
        		publication.setAttribute("pubMedId", pubMedId);
        		String[] meshIds = cols[1].split(",");
        		for (String meshId : meshIds) {
					publication.addToCollection("meshTerms", getMeshTerm(meshId));
				}
        		store(publication);
        		publicationMap.put(pubMedId, publication.getIdentifier());
    		}
    	}
	}
    
	/**
	 * Retrieve the publications to be updated
	 * 
	 * @return a List of PubMed IDs
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected Set<String> getPubMedIds() throws Exception {
		Query q = new Query();
		QueryClass qc = new QueryClass(Publication.class);
		q.addFrom(qc);
		q.addToSelect(qc);

		ObjectStore os = ObjectStoreFactory.getObjectStore(osAlias);
		
		List<Publication> publications = (List<Publication>) ((List) os.executeSingleton(q));
		Iterator<Publication> iterator = publications.iterator();
		Set<String> pubmedIds = new HashSet<String>();
		while (iterator.hasNext()) {
			Publication publication = iterator.next();
			pubmedIds.add(publication.getPubMedId());
		}
		LOG.info(String.format("Found %d pubmed identifiers.", pubmedIds.size()));
		
		return pubmedIds;
	}

	private Map<String, String> meshTermMap = new HashMap<String, String>();
	
	private String getMeshTerm(String meshId) throws ObjectStoreException {
		String ret = meshTermMap.get(meshId);
		if (ret == null) {
			Item item = createItem("MeshTerm");
			item.setAttribute("identifier", meshId);
			store(item);
			ret = item.getIdentifier();
			meshTermMap.put(meshId, ret);
		}
		return ret;
	}
}

