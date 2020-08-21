package org.intermine.bio.dataconversion;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreFactory;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;

public class IdSetLoader {
	private static final Logger LOG = LogManager.getLogger(IdSetLoader.class);

	Set<String> idSet = new HashSet<String>();
	private String className;
	private String osAlias;
	private String idName;
	
	public IdSetLoader(String osAlias, String className, String idName) {
		this.osAlias = osAlias;
		this.className = className;
		this.idName = idName;
	}

	@SuppressWarnings("unchecked")
	public void loadIds() throws Exception {
		LOG.info("Start loading diseaseterm id");
		ObjectStore os = ObjectStoreFactory.getObjectStore(osAlias);

		Query q = new Query();
		QueryClass qcDiseaseTerm = new QueryClass(os.getModel().getClassDescriptorByName(className).getType());

		QueryField qfIdentifier = new QueryField(qcDiseaseTerm, idName);

		q.addFrom(qcDiseaseTerm);
		q.addToSelect(qfIdentifier);

		Results results = os.execute(q);
		Iterator<Object> iterator = results.iterator();
		while (iterator.hasNext()) {
			ResultsRow<String> rr = (ResultsRow<String>) iterator.next();
			idSet.add(rr.get(0));
		}
		LOG.info("loaded "+ idSet.size()+" "+ className + " id " );
	}
	public boolean hasId(String id) {
		return idSet.contains(id);
	}

}
