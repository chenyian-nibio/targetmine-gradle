package org.intermine.bio.dataconversion;

import java.util.HashMap;
import java.util.Map;

import org.intermine.dataconversion.DataConverter;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.xml.full.Item;

public class ItemCreator {
	private DataConverter converter;
	private String className;
	private String identifierName;
	private Map<String, String> itemMap = new HashMap<>();
	public ItemCreator(DataConverter converter,String className,String identifierName) {
		this.converter = converter;
		this.className = className;
		this.identifierName = identifierName;
	}
	public String createItemRef(String identifier) throws ObjectStoreException {
		if(Utils.isEmpty(identifier)){
			return null;
		}
		if(itemMap.containsKey(identifier)){
			return itemMap.get(identifier);
		}
		Item item = converter.createItem(className);
		item.setAttribute(identifierName, identifier);
		converter.store(item);
		itemMap.put(identifier, item.getIdentifier());
		return item.getIdentifier();
	}

}
