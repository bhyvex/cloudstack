// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.globo.networkapi.api;

import javax.inject.Inject;

import org.apache.cloudstack.acl.ControlledEntity.ACLType;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.response.DomainResponse;
import org.apache.cloudstack.api.response.NetworkACLResponse;
import org.apache.cloudstack.api.response.NetworkOfferingResponse;
import org.apache.cloudstack.api.response.NetworkResponse;
import org.apache.cloudstack.api.response.PhysicalNetworkResponse;
import org.apache.cloudstack.api.response.ProjectResponse;
import org.apache.cloudstack.api.response.ZoneResponse;
import org.apache.log4j.Logger;

import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.network.Network;
import com.cloud.user.UserContext;
import com.cloud.utils.exception.CloudRuntimeException;
import com.globo.networkapi.manager.NetworkAPIService;

@APICommand(name = "addNetworkApiVlan", responseObject=NetworkResponse.class, description="Adds a vlan/network from Network API")
public class AddNetworkApiVlanCmd extends BaseCmd {

    public static final Logger s_logger = Logger.getLogger(AddNetworkApiVlanCmd.class.getName());
    private static final String s_name = "addnetworkapivlanresponse";
    
    @Inject
    NetworkAPIService _ntwkAPIService;

    /* Parameters */
    @Parameter(name=ApiConstants.VLAN_ID, type=CommandType.LONG, required = true, description="VLAN ID.")
    private Long vlanId;
    
    @Parameter(name=ApiConstants.ZONE_ID, type=CommandType.UUID, entityType = ZoneResponse.class,
            required=true, description="the Zone ID for the network")
    private Long zoneId;
    
    @Parameter(name=ApiConstants.NETWORK_OFFERING_ID, type=CommandType.UUID, entityType = NetworkOfferingResponse.class,
            required=true, description="the network offering id")
    private Long networkOfferingId;
    
    @Parameter(name=ApiConstants.PHYSICAL_NETWORK_ID, type=CommandType.UUID, entityType = PhysicalNetworkResponse.class,
            required=true, description="the Physical Network ID the network belongs to")
    private Long physicalNetworkId;

    @Parameter(name=ApiConstants.NETWORK_DOMAIN, type=CommandType.STRING, description="network domain")
    private String networkDomain;
    
    @Parameter(name=ApiConstants.ACL_TYPE, type=CommandType.STRING, description="Access control type; supported values" +
            " are account and domain.")
    private String aclType;

    @Parameter(name=ApiConstants.ACCOUNT, type=CommandType.STRING, description="account who will own the network")
    private String accountName;

    @Parameter(name=ApiConstants.PROJECT_ID, type=CommandType.UUID, entityType = ProjectResponse.class,
            description="an optional project for the ssh key")
    private Long projectId;

    @Parameter(name=ApiConstants.DOMAIN_ID, type=CommandType.UUID, entityType = DomainResponse.class,
            description="domain ID of the account owning a network")
    private Long domainId;

    @Parameter(name=ApiConstants.SUBDOMAIN_ACCESS, type=CommandType.BOOLEAN, description="Defines whether to allow" +
            " subdomains to use networks dedicated to their parent domain(s). Should be used with aclType=Domain, defaulted to allow.subdomain.network.access global config if not specified")
    private Boolean subdomainAccess;

    @Parameter(name=ApiConstants.DISPLAY_NETWORK, type=CommandType.BOOLEAN, description="an optional field, whether to the display the network to the end user or not.")
    private Boolean displayNetwork;

    @Parameter(name=ApiConstants.ACL_ID, type=CommandType.UUID, entityType = NetworkACLResponse.class,
            description="Network ACL Id associated for the network")
    private Long aclId;

    /* Accessors */
    public Long getVlanId() {
        return vlanId;
    }

    public Long getZoneId() {
    	return zoneId;
    }
    
    public Long getNetworkOfferingId() {
    	return networkOfferingId;
    }

    public Long getPhysicalNetworkId() {
    	return physicalNetworkId;
    }
    
    public ACLType getACLType() {
    	if ("account".equalsIgnoreCase(aclType)) {
    		return ACLType.Account;
    	} else if ("domain".equalsIgnoreCase(aclType)) {
    		return ACLType.Domain;
    	} else {
    		return null;
    	}
    }

    /* Implementation */
    @Override
    public void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException, ResourceAllocationException {
        try {
        	s_logger.debug("addNetworkAPIVlan command with vlanId=" + vlanId + " zoneId=" + zoneId + " networkOfferingId=" + networkOfferingId + " physicalNetworkId=" + physicalNetworkId +
        			" networkDomain=" +  networkDomain + " aclType=" + aclType + " accountName=" + accountName + " projectId=" + projectId +
        			" domainId=" + domainId + " subdomainAccess=" + subdomainAccess + " displayNetwork=" + displayNetwork + " aclId=" + aclId);
        	Network network = _ntwkAPIService.createNetworkFromNetworkAPIVlan(vlanId, null, zoneId, networkOfferingId, physicalNetworkId, networkDomain, getACLType(), accountName,
        			projectId, domainId, subdomainAccess, displayNetwork, aclId);
        	if (network != null) {
        		NetworkResponse response = _responseGenerator.createNetworkResponse(network);
        		response.setResponseName(getCommandName());
        		this.setResponseObject(response);
        	} else {
        		throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create network from NetworkAPI.");
        	}
        }  catch (InvalidParameterValueException invalidParamExcp) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, invalidParamExcp.getMessage());
        } catch (CloudRuntimeException runtimeExcp) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, runtimeExcp.getMessage());
        }
    }
 
    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return UserContext.current().getCaller().getId();
    }
}
