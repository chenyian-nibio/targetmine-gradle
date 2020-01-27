package org.intermine.bio.dataconversion;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.intermine.dataconversion.DataConverter;
import org.intermine.metadata.ConstraintOp;
import org.intermine.model.InterMineObject;
import org.intermine.objectstore.ObjectStore;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.objectstore.ObjectStoreFactory;
import org.intermine.objectstore.query.Query;
import org.intermine.objectstore.query.QueryClass;
import org.intermine.objectstore.query.QueryField;
import org.intermine.objectstore.query.QueryValue;
import org.intermine.objectstore.query.Results;
import org.intermine.objectstore.query.ResultsRow;
import org.intermine.objectstore.query.SimpleConstraint;
import org.intermine.xml.full.Item;

public class GeneIdFinder {
	private BioDBConverter converter;
	private String osAlias;
	private Map<String, String> geneMap = new HashMap<>();
	public GeneIdFinder(String osAlias,DataConverter converter) {

		this.osAlias = osAlias;
	}
    Map<String, String> synonymSubjectIdMap = new HashMap<>();
    public String getGenePrimayIdBySynonym(String refCore) throws Exception {
    	if(Utils.isEmpty(refCore)) {
    		return null;
    	}
    	if(synonymSubjectIdMap.containsKey(refCore)) {
    		return synonymSubjectIdMap.get(refCore);
    	}
        ObjectStore os = ObjectStoreFactory.getObjectStore(osAlias);

        Query q = new Query();
        QueryClass qcSynonym = new QueryClass(os.getModel().
                getClassDescriptorByName("Synonym").getType());

        q.addFrom(qcSynonym);
        q.addToSelect(qcSynonym);
        QueryField qcSynonymInterMineValue = new QueryField(qcSynonym, "value");
        SimpleConstraint sc = new SimpleConstraint(qcSynonymInterMineValue, ConstraintOp.EQUALS, new QueryValue(refCore));
        q.setConstraint(sc);
        // query = select * from synonym where value = refCore;
        Results results = os.execute(q);
        Iterator<Object> iterator = results.iterator();

        while (iterator.hasNext()) {
            ResultsRow<InterMineObject> rr = (ResultsRow<InterMineObject>) iterator.next();
            InterMineObject p = rr.get(0);

            // synonym.subject is 'gene' Item, get subject.
            InterMineObject geneItem = (InterMineObject) p.getFieldValue("subject");
            String genePrimaryId = (String) geneItem.getFieldValue("primaryIdentifier");
            if (genePrimaryId != null) {
                synonymSubjectIdMap.put(refCore, genePrimaryId);
                return genePrimaryId;
            }
        }
        return null;
    }
    Map<String, String> entrezGeneIdMap = new HashMap<>();
    public String getGenePrimayIdByEntrezGeneId(String entrezGeneId) throws Exception {
    	if(Utils.isEmpty(entrezGeneId)) {
    		return null;
    	}
    	if(entrezGeneIdMap.containsKey(entrezGeneId)) {
    		return entrezGeneIdMap.get(entrezGeneId);
    	}
        ObjectStore os = ObjectStoreFactory.getObjectStore(osAlias);

        Query q = new Query();
        QueryClass gene = new QueryClass(os.getModel().
                getClassDescriptorByName("Gene").getType());

        q.addFrom(gene);
        q.addToSelect(gene);
        QueryField qcGeneNcbiGeneId = new QueryField(gene, "ncbigeneid");
        SimpleConstraint sc = new SimpleConstraint(qcGeneNcbiGeneId, ConstraintOp.EQUALS, new QueryValue(entrezGeneId));
        q.setConstraint(sc);
        Results results = os.execute(q);
        Iterator<Object> iterator = results.iterator();
        String genePrimaryId = null;
        while (iterator.hasNext()) {
            ResultsRow<InterMineObject> rr = (ResultsRow<InterMineObject>) iterator.next();
            InterMineObject p = rr.get(0);

            // synonym.subject is 'gene' Item, get subject.
            InterMineObject geneItem = (InterMineObject) p.getFieldValue("subject");
            genePrimaryId = (String) geneItem.getFieldValue("primaryIdentifier");
            break;
        }
    	entrezGeneIdMap.put(entrezGeneId, genePrimaryId);
        return genePrimaryId;
    }

    public String getGeneRef(String primaryIdentifier) throws ObjectStoreException {
        if (StringUtils.isEmpty(primaryIdentifier)) {
            return "";
        }
        String ret = geneMap.get(primaryIdentifier);

        if (ret == null) {
            Item item = converter.createItem("Gene");
            item.setAttribute("primaryIdentifier", primaryIdentifier);
            converter.store(item);
            ret = item.getIdentifier();
            geneMap.put(primaryIdentifier, ret);
        }
        return ret;
    }

}
