package com.globo.networkapi.element;

import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.Network;
import com.cloud.org.Cluster;


public interface NetworkAPIService {
	
	public Object allocateVlan(Network network, Cluster cluster) throws ResourceUnavailableException;
	

}
