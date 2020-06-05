package org.intermine.bio.dataconversion;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.intermine.metadata.ConstraintOp;
import org.intermine.model.InterMineObject;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreFactory;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.QueryValue;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.objectstore.query.SimpleConstraint;

public class DBIDFinder {
	private String osAlias;
	private String className;
	private String searchPropName;
	private String identifierName;
	
    public DBIDFinder(String osAlias, String className, String searchPropName, String identifierName) {
		this.osAlias = osAlias;
		this.className = className;
		this.searchPropName = searchPropName;
		this.identifierName = identifierName;
	}
	Map<String, String> cache = new HashMap<>();
    public String getIdentifierByValue(String value) throws Exception {
    	if(Utils.isEmpty(value)) {
    		return null;
    	}
    	if(cache.containsKey(value)) {
    		return cache.get(value);
    	}
        ObjectStore os = ObjectStoreFactory.getObjectStore(osAlias);

        Query q = new Query();
        QueryClass gene = new QueryClass(os.getModel().
                getClassDescriptorByName(className).getType());

        q.addFrom(gene);
        q.addToSelect(gene);
        QueryField qcGeneNcbiGeneId = new QueryField(gene, searchPropName);
        SimpleConstraint sc = new SimpleConstraint(qcGeneNcbiGeneId, ConstraintOp.EQUALS, new QueryValue(value));
        q.setConstraint(sc);
        Results results = os.execute(q);
        Iterator<Object> iterator = results.iterator();
        String identifier = null;
        while (iterator.hasNext()) {
            ResultsRow<InterMineObject> rr = (ResultsRow<InterMineObject>) iterator.next();
            InterMineObject p = rr.get(0);

            identifier = (String) p.getFieldValue(identifierName);
            break;
        }
        cache.put(value, identifier);
        return identifier;
    }

}
