package com.globo.globonetwork.cloudstack.manager;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ejb.Local;
import javax.inject.Inject;
import javax.naming.ConfigurationException;

import org.apache.cloudstack.acl.ControlledEntity.ACLType;
import org.apache.cloudstack.acl.SecurityChecker.AccessType;
import org.apache.cloudstack.context.CallContext;
import org.apache.cloudstack.engine.orchestration.service.NetworkOrchestrationService;
import org.apache.cloudstack.framework.config.ConfigKey;
import org.apache.cloudstack.framework.config.dao.ConfigurationDao;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.configuration.Config;
import com.cloud.configuration.ConfigurationManager;
import com.cloud.configuration.Resource.ResourceType;
import com.cloud.dc.DataCenter;
import com.cloud.dc.DataCenterVO;
import com.cloud.dc.dao.DataCenterDao;
import com.cloud.dc.dao.HostPodDao;
import com.cloud.deploy.DeployDestination;
import com.cloud.domain.DomainVO;
import com.cloud.domain.dao.DomainDao;
import com.cloud.exception.CloudException;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientAddressCapacityException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.InsufficientVirtualNetworkCapcityException;
import com.cloud.exception.InvalidParameterValueException;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.host.Host;
import com.cloud.host.Host.Type;
import com.cloud.host.HostVO;
import com.cloud.host.dao.HostDao;
import com.cloud.network.Network;
import com.cloud.network.Network.GuestType;
import com.cloud.network.Network.Provider;
import com.cloud.network.NetworkModel;
import com.cloud.network.NetworkService;
import com.cloud.network.PhysicalNetwork;
import com.cloud.network.dao.NetworkDao;
import com.cloud.network.dao.NetworkServiceMapDao;
import com.cloud.network.dao.NetworkVO;
import com.cloud.network.dao.PhysicalNetworkDao;
import com.cloud.network.dao.PhysicalNetworkVO;
import com.cloud.network.guru.NetworkGuru;
import com.cloud.offerings.NetworkOfferingVO;
import com.cloud.offerings.dao.NetworkOfferingDao;
import com.cloud.org.Grouping;
import com.cloud.projects.Project;
import com.cloud.projects.ProjectManager;
import com.cloud.resource.ResourceManager;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.DomainManager;
import com.cloud.user.UserVO;
import com.cloud.user.dao.UserDao;
import com.cloud.utils.Journal;
import com.cloud.utils.Pair;
import com.cloud.utils.StringUtils;
import com.cloud.utils.component.PluggableService;
import com.cloud.utils.db.DB;
import com.cloud.utils.db.Transaction;
import com.cloud.utils.db.TransactionCallback;
import com.cloud.utils.db.TransactionCallbackWithException;
import com.cloud.utils.db.TransactionStatus;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.utils.net.NetUtils;
import com.cloud.vm.Nic;
import com.cloud.vm.NicProfile;
import com.cloud.vm.ReservationContext;
import com.cloud.vm.ReservationContextImpl;
import com.cloud.vm.VMInstanceVO;
import com.cloud.vm.VirtualMachineProfile;
import com.cloud.vm.dao.NicDao;
import com.cloud.vm.dao.VMInstanceDao;
import com.globo.globonetwork.client.model.Vlan;
import com.globo.globonetwork.cloudstack.GloboNetworkEnvironmentVO;
import com.globo.globonetwork.cloudstack.GloboNetworkNetworkVO;
import com.globo.globonetwork.cloudstack.GloboNetworkVipAccVO;
import com.globo.globonetwork.cloudstack.api.AddGloboNetworkEnvironmentCmd;
import com.globo.globonetwork.cloudstack.api.AddGloboNetworkHostCmd;
import com.globo.globonetwork.cloudstack.api.AddGloboNetworkRealToVipCmd;
import com.globo.globonetwork.cloudstack.api.AddGloboNetworkVipToAccountCmd;
import com.globo.globonetwork.cloudstack.api.AddGloboNetworkVlanCmd;
import com.globo.globonetwork.cloudstack.api.AddNetworkViaGloboNetworkCmd;
import com.globo.globonetwork.cloudstack.api.DelGloboNetworkRealFromVipCmd;
import com.globo.globonetwork.cloudstack.api.GenerateUrlForEditingVipCmd;
import com.globo.globonetwork.cloudstack.api.ListAllEnvironmentsFromGloboNetworkCmd;
import com.globo.globonetwork.cloudstack.api.ListGloboNetworkEnvironmentsCmd;
import com.globo.globonetwork.cloudstack.api.ListGloboNetworkRealsCmd;
import com.globo.globonetwork.cloudstack.api.ListGloboNetworkVipsCmd;
import com.globo.globonetwork.cloudstack.api.RemoveGloboNetworkEnvironmentCmd;
import com.globo.globonetwork.cloudstack.api.RemoveGloboNetworkVipCmd;
import com.globo.globonetwork.cloudstack.commands.ActivateNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.AddAndEnableRealInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.CreateNewVlanInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.DeallocateVlanFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.DisableAndRemoveRealInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GenerateUrlForEditingVipCommand;
import com.globo.globonetwork.cloudstack.commands.GetVipInfoFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GetVlanInfoFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GloboNetworkErrorAnswer;
import com.globo.globonetwork.cloudstack.commands.ListAllEnvironmentsFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.RegisterEquipmentAndIpInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.RemoveNetworkInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.RemoveVipFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.UnregisterEquipmentAndIpInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.ValidateNicInVlanCommand;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkEnvironmentDao;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkNetworkDao;
import com.globo.globonetwork.cloudstack.dao.GloboNetworkVipAccDao;
import com.globo.globonetwork.cloudstack.exception.CloudstackGloboNetworkException;
import com.globo.globonetwork.cloudstack.resource.GloboNetworkResource;
import com.globo.globonetwork.cloudstack.response.GloboNetworkAllEnvironmentResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkAllEnvironmentResponse.Environment;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVipResponse.Real;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVlanResponse;

@Component
@Local({GloboNetworkService.class, PluggableService.class})
public class GloboNetworkManager implements GloboNetworkService, PluggableService {

	private static final Logger s_logger = Logger
			.getLogger(GloboNetworkManager.class);

	static final int NUMBER_OF_RESERVED_IPS_FROM_START = 5;
	static final int NUMBER_OF_RESERVED_IPS_BEFORE_END = 5;
	
	private static final ConfigKey<String> GloboNetworkVIPServerUrl = new ConfigKey<String>("Advanced", String.class, "globonetwork.vip.server.url", "", "Server URL to generate a new VIP request", true, ConfigKey.Scope.Global);

	// DAOs
	@Inject
	DomainDao _domainDao;
	@Inject
	HostDao _hostDao;
	@Inject
	DataCenterDao _dcDao;
	@Inject
	HostPodDao _hostPodDao;
	@Inject
	PhysicalNetworkDao _physicalNetworkDao;
	@Inject
	NetworkOfferingDao _networkOfferingDao;
	@Inject
	ConfigurationDao _configDao;
	@Inject
	UserDao _userDao;
	@Inject
	NetworkDao _ntwkDao;
    @Inject
    NicDao _nicDao;
	@Inject
	NetworkServiceMapDao _ntwkSrvcDao;
	@Inject
	GloboNetworkNetworkDao _globoNetworkNetworkDao;
	@Inject
	GloboNetworkEnvironmentDao _globoNetworkEnvironmentDao;
	@Inject
	GloboNetworkVipAccDao _globoNetworkVipAccDao;
	
	// Managers
	@Inject
	NetworkModel _networkManager;
	@Inject
	AgentManager _agentMgr;
	@Inject
	ConfigurationManager _configMgr;
	@Inject
	ResourceManager _resourceMgr;
	@Inject
	DomainManager _domainMgr;
	@Inject
	NetworkOrchestrationService _networkMgr;
	@Inject
	AccountManager _accountMgr;
    @Inject
    ProjectManager _projectMgr;
	@Inject
	NetworkService _ntwSvc;
	@Inject
	VMInstanceDao _vmDao;
	
	@Override
	public boolean canEnable(Long physicalNetworkId) {
		if (physicalNetworkId == null) {
			return false;
		}
		List<GloboNetworkEnvironmentVO> list = _globoNetworkEnvironmentDao.listByPhysicalNetworkId(physicalNetworkId);
		if (list.isEmpty()) {
			throw new CloudRuntimeException("Before enabling GloboNetwork you must add an environment to your physical interface");
		}
		return true;
	}

	@Override
    @DB
	public Network createNetwork(String name, String displayText, Long zoneId,
			Long networkOfferingId, Long napiEnvironmentId,
			String networkDomain, ACLType aclType, String accountName,
			Long projectId, Long domainId, Boolean subdomainAccess,
			Boolean displayNetwork, Long aclId)
			throws ResourceAllocationException, ResourceUnavailableException,
			ConcurrentOperationException, InsufficientCapacityException {

		Account caller = CallContext.current().getCallingAccount();

		if ((accountName != null && domainId != null) || projectId != null) {
			_accountMgr.finalizeOwner(caller, accountName, domainId,
					projectId);
		}

		DataCenter zone = _dcDao.findById(zoneId);
		if (zone == null) {
			throw new InvalidParameterValueException(
					"Specified zone id was not found");
		}
		
		Long physicalNetworkId = null;
		if (napiEnvironmentId != null) {
			GloboNetworkEnvironmentVO napiEnvironmentVO = null;
			for (PhysicalNetwork pNtwk : _physicalNetworkDao.listByZone(zoneId)) {
				napiEnvironmentVO = _globoNetworkEnvironmentDao.findByPhysicalNetworkIdAndEnvironmentId(pNtwk.getId(), napiEnvironmentId);
				if (napiEnvironmentVO != null) {
					break;
				}
			}
			if (napiEnvironmentVO == null) {
				throw new InvalidParameterValueException("Unable to find a relationship between GloboNetwork environment and physical network");
			}
			physicalNetworkId = napiEnvironmentVO.getPhysicalNetworkId();
		} else {
			throw new InvalidParameterValueException("GloboNetwork enviromentId was not found");
		}
		
		Answer answer = createNewVlan(zoneId, name, displayText, napiEnvironmentId);

		GloboNetworkVlanResponse response = (GloboNetworkVlanResponse) answer;
		Long napiVlanId = response.getVlanId();

		Network network = null;
		
		try {
			network = this.createNetworkFromGloboNetworkVlan(napiVlanId, napiEnvironmentId, zoneId,
				networkOfferingId, physicalNetworkId, networkDomain, aclType,
				accountName, projectId, domainId, subdomainAccess,
				displayNetwork, aclId);
		} catch (Exception e) {
			// Exception when creating network in Cloudstack. Roll back transaction in GloboNetwork
			s_logger.error("Reverting network creation in GloboNetwork due to error creating network", e);
			this.deallocateVlanFromGloboNetwork(zoneId, napiVlanId);
			
			throw new ResourceAllocationException(e.getLocalizedMessage(), ResourceType.network);
		}

		// if the network offering has persistent set to true, implement the
		// network
		// FIXME While we have same issues with ACL API with net not in equipment, all
		// networks are considered persistent.
		// NetworkOfferingVO ntwkOff = _networkOfferingDao.findById(networkOfferingId);
		if (true /*ntwkOff.getIsPersistent()*/) {
			try {
				if (network.getState() == Network.State.Setup) {
					s_logger.debug("Network id=" + network.getId()
							+ " is already provisioned");
					return network;
				}
				DeployDestination dest = new DeployDestination(zone, null,
						null, null);
				UserVO callerUser = _userDao.findById(CallContext.current()
						.getCallingUserId());
				Journal journal = new Journal.LogJournal("Implementing "
						+ network, s_logger);
				ReservationContext context = new ReservationContextImpl(UUID
						.randomUUID().toString(), journal, callerUser, caller);
				s_logger.debug("Implementing network "
						+ network
						+ " as a part of network provision for persistent network");
				@SuppressWarnings("unchecked")
				Pair<NetworkGuru, NetworkVO> implementedNetwork = (Pair<NetworkGuru, NetworkVO>) _networkMgr
						.implementNetwork(network.getId(), dest, context);
				if (implementedNetwork.first() == null) {
					s_logger.warn("Failed to provision the network " + network);
				}
				network = implementedNetwork.second();
			} catch (ResourceUnavailableException ex) {
				s_logger.warn("Failed to implement persistent guest network "
						+ network + "due to ", ex);
				CloudRuntimeException e = new CloudRuntimeException(
						"Failed to implement persistent guest network");
				e.addProxyObject(network.getUuid(), "networkId");
				throw e;
			}
		}


		return network;
	}

	@Override
    @DB
	public Network createNetworkFromGloboNetworkVlan(final Long vlanId, final Long napiEnvironmentId, Long zoneId,
			final Long networkOfferingId, final Long physicalNetworkId,
			final String networkDomain, final ACLType aclType, String accountName, Long projectId,
			final Long domainId, final Boolean subdomainAccess, final Boolean displayNetwork,
			Long aclId) throws CloudException, ResourceUnavailableException,
			ResourceAllocationException, ConcurrentOperationException,
			InsufficientCapacityException {

		final Account caller = CallContext.current().getCallingAccount();
		
		final Account owner;
		if ((accountName != null && domainId != null) || projectId != null) {
			owner = _accountMgr.finalizeOwner(caller, accountName, domainId,
					projectId);
		} else {
			owner = caller;
		}

        // Only domain and account ACL types are supported in Action.
        if (aclType == null || !(aclType == ACLType.Domain || aclType == ACLType.Account)) {
            throw new InvalidParameterValueException("AclType should be " + ACLType.Domain + " or " +
            		ACLType.Account + " for network of type " + Network.GuestType.Shared);
        }
		
		final boolean isDomainSpecific = true;

		// Validate network offering
		final NetworkOfferingVO ntwkOff = _networkOfferingDao
				.findById(networkOfferingId);
		if (ntwkOff == null || ntwkOff.isSystemOnly()) {
			InvalidParameterValueException ex = new InvalidParameterValueException(
					"Unable to find network offering by specified id");
			if (ntwkOff != null) {
				ex.addProxyObject(ntwkOff.getUuid(), "networkOfferingId");
			}
			throw ex;
		} else if (GuestType.Shared != ntwkOff.getGuestType()) {
			InvalidParameterValueException ex = new InvalidParameterValueException(
					"GloboNetwork can handle only network offering with guest type shared");
			if (ntwkOff != null) {
				ex.addProxyObject(ntwkOff.getUuid(), "networkOfferingId");
			}
			throw ex;
		}
		// validate physical network and zone
		// Check if physical network exists
		final PhysicalNetwork pNtwk;
		if (physicalNetworkId != null) {
			pNtwk = _physicalNetworkDao.findById(physicalNetworkId);
			if (pNtwk == null) {
				throw new InvalidParameterValueException(
						"Unable to find a physical network having the specified physical network id");
			}
		} else {
			throw new InvalidParameterValueException(
					"invalid physicalNetworkId " + physicalNetworkId);
		}

		if (zoneId == null) {
			zoneId = pNtwk.getDataCenterId();
		}

		final DataCenter zone = _dcDao.findById(zoneId);
		if (zone == null) {
			throw new InvalidParameterValueException(
					"Specified zone id was not found");
		}

		if (Grouping.AllocationState.Disabled == zone.getAllocationState()
				&& !_accountMgr.isAdmin(caller.getType())) {
			// See DataCenterVO.java
			PermissionDeniedException ex = new PermissionDeniedException(
					"Cannot perform this operation since specified Zone is currently disabled");
			ex.addProxyObject(zone.getUuid(), "zoneId");
			throw ex;
		}

		if (domainId != null) {
			DomainVO domain = _domainDao.findById(domainId);
			if (domain == null) {
				throw new InvalidParameterValueException(
						"Unable to find domain by specified id");
			}
			_accountMgr.checkAccess(caller, domain);
		}

		if (_configMgr.isOfferingForVpc(ntwkOff)) {
			throw new InvalidParameterValueException(
					"Network offering can't be used for VPC networks");
		}

		// CallContext.register(CallContext.current().getCallingUserId(), owner.getAccountId());

		/////// GloboNetwork specific code ///////

		// Get VlanInfo from GloboNetwork
		GetVlanInfoFromGloboNetworkCommand cmd = new GetVlanInfoFromGloboNetworkCommand();
		cmd.setVlanId(vlanId);

		final GloboNetworkVlanResponse response = (GloboNetworkVlanResponse) callCommand(cmd, zoneId);

		long networkAddresLong = response.getNetworkAddress().toLong();
		String networkAddress = NetUtils.long2Ip(networkAddresLong);
		final String netmask = response.getMask().ip4();
		final String cidr = NetUtils.ipAndNetMaskToCidr(networkAddress, netmask);

		String ranges[] = NetUtils.ipAndNetMaskToRange(networkAddress, netmask);
		final String gateway = ranges[0];
		final String startIP = NetUtils.long2Ip(NetUtils.ip2Long(ranges[0])
				+ NUMBER_OF_RESERVED_IPS_FROM_START);
		final String endIP = NetUtils.long2Ip(NetUtils.ip2Long(ranges[1])
				- NUMBER_OF_RESERVED_IPS_BEFORE_END);
		final Long vlanNum = response.getVlanNum();
		// NO IPv6 support yet
		final String startIPv6 = null;
		final String endIPv6 = null;
		final String ip6Gateway = null;
		final String ip6Cidr = null;

		s_logger.info("Creating network with name " + response.getVlanName()
				+ " (" + response.getVlanId() + "), network " + networkAddress
				+ " gateway " + gateway + " startIp " + startIP + " endIp "
				+ endIP + " cidr " + cidr);
		/////// End of GloboNetwork specific code ///////

		 Network network = Transaction.execute(new TransactionCallbackWithException<Network, CloudException>() {

			@Override
			public Network doInTransaction(TransactionStatus status) throws CloudException {
				Boolean newSubdomainAccess = subdomainAccess;
				Long sharedDomainId = null;
				if (isDomainSpecific) {
					if (domainId != null) {
						sharedDomainId = domainId;
					} else {
						sharedDomainId = owner.getDomainId();
						newSubdomainAccess = true;
					}
				}
				
				String newNetworkDomain = networkDomain;
				if (!StringUtils.isNotBlank(newNetworkDomain)) {
					/* Create new domain in DNS */
					String domainSuffix = _configDao.getValue(Config.GloboNetworkDomainSuffix.key());
					// domainName is of form 'zoneName-vlanNum.domainSuffix'
					if (domainSuffix == null) {
						domainSuffix = "";
					} else if (!domainSuffix.startsWith(".")) {
						domainSuffix = "." + domainSuffix;
					}
			    	newNetworkDomain = (zone.getName() + "-" + String.valueOf(response.getVlanNum()) + domainSuffix).toLowerCase();
				}

				Network network = _networkMgr.createGuestNetwork(
						networkOfferingId.longValue(), response.getVlanName(),
						response.getVlanDescription(), gateway, cidr,
						String.valueOf(response.getVlanNum()), newNetworkDomain, owner,
						sharedDomainId, pNtwk, zone.getId(), aclType, newSubdomainAccess, 
						null, // vpcId,
						ip6Gateway,
						ip6Cidr,
						displayNetwork,
						null // isolatedPvlan
						);
				
				// Save relashionship with napi and network
				GloboNetworkNetworkVO napiNetworkVO = new GloboNetworkNetworkVO(vlanId,
						network.getId(), napiEnvironmentId);
				napiNetworkVO = _globoNetworkNetworkDao.persist(napiNetworkVO);

				// if (caller.getType() == Account.ACCOUNT_TYPE_ADMIN || caller.getType() == Account.ACCOUNT_TYPE_DOMAIN_ADMIN) {
					// Create vlan ip range
					_configMgr.createVlanAndPublicIpRange(pNtwk.getDataCenterId(),
							network.getId(), physicalNetworkId, false, (Long) null,
							startIP, endIP, gateway, netmask, vlanNum.toString(), null,
							startIPv6, endIPv6, ip6Gateway, ip6Cidr);
				// }
				return network;
			}
		});
		return network;
	}

	protected GloboNetworkVlanResponse createNewVlan(Long zoneId, String name,
			String description, Long globoNetworkEnvironmentId) {

		CreateNewVlanInGloboNetworkCommand cmd = new CreateNewVlanInGloboNetworkCommand();
		cmd.setVlanName(name);
		cmd.setVlanDescription(description);
		cmd.setGloboNetworkEnvironmentId(globoNetworkEnvironmentId);

		return (GloboNetworkVlanResponse) callCommand(cmd, zoneId);
	}

	private Answer callCommand(Command cmd, Long zoneId) {
		
		HostVO napiHost = getGloboNetworkHost(zoneId);
		if (napiHost == null) {
			throw new CloudstackGloboNetworkException("Could not find the GloboNetwork resource");
		}
		
		Answer answer = _agentMgr.easySend(napiHost.getId(), cmd);
		if (answer == null || !answer.getResult()) {
			
			if (answer instanceof GloboNetworkErrorAnswer) {
				GloboNetworkErrorAnswer napiAnswer = (GloboNetworkErrorAnswer) answer; 
				throw new CloudstackGloboNetworkException(napiAnswer.getNapiCode(), napiAnswer.getNapiDescription());
			} else {
				String msg = "Error executing command " + cmd + ". Maybe GloboNetwork Host is down";
				msg = answer == null ? msg : answer.getDetails();
				throw new CloudRuntimeException(msg);
			}
		}
		
		return answer;
	}
	
	private HostVO getGloboNetworkHost(Long zoneId) {
		return _hostDao.findByTypeNameAndZoneId(zoneId, Provider.GloboNetwork.getName(), Type.L2Networking);
	}

	@Override
	public Network validateNic(NicProfile nicProfile,
			VirtualMachineProfile vm, Network network)
			throws InsufficientVirtualNetworkCapcityException,
			InsufficientAddressCapacityException {

		ValidateNicInVlanCommand cmd = new ValidateNicInVlanCommand();
		cmd.setNicIp(nicProfile.getIp4Address());
		cmd.setVlanId(getGloboNetworkVlanId(network.getId()));
		cmd.setVlanNum(Long.valueOf(getVlanNum(nicProfile.getBroadCastUri())));

		String msg = "Unable to validate nic " + nicProfile + " from VM " + vm;
		Answer answer = this.callCommand(cmd, network.getDataCenterId());
		if (answer == null || !answer.getResult()) {
			msg = answer == null ? msg : answer.getDetails();
			throw new InsufficientVirtualNetworkCapcityException(msg, Nic.class, nicProfile.getId());
		}

		// everything is ok
		return network;
	}

	@Override
	public void implementNetwork(Network network) throws ConfigurationException {
		Long vlanId = getGloboNetworkVlanId(network.getId());
		if (vlanId == null) {
			throw new CloudRuntimeException("Inconsistency. Network "
					+ network.getName()
					+ " there is not relation with GloboNetwork");
		}

		GetVlanInfoFromGloboNetworkCommand cmd = new GetVlanInfoFromGloboNetworkCommand();
		cmd.setVlanId(vlanId);

		Answer answer = callCommand(cmd, network.getDataCenterId());

		GloboNetworkVlanResponse vlanResponse = (GloboNetworkVlanResponse) answer;
		vlanResponse.getNetworkAddress();
		Long networkId = vlanResponse.getNetworkId();
		if (!vlanResponse.isActive()) {
			// Create network in equipment
			ActivateNetworkCommand cmd_creation = new ActivateNetworkCommand(vlanId,
					networkId);
			Answer creation_answer = callCommand(cmd_creation, network.getDataCenterId());
			if (creation_answer == null || !creation_answer.getResult()) {
				throw new CloudRuntimeException(
						"Unable to create network in GloboNetwork: VlanId "
								+ vlanId + " networkId " + networkId);
			}
			s_logger.info("Network ready to use: VlanId " + vlanId
					+ " networkId " + networkId);
		} else {
			s_logger.warn("Network already created in GloboNetwork: VlanId "
					+ vlanId + " networkId " + networkId);
		}
	}

	/**
	 * Returns VlanId (in GloboNetwork) given an Network. If network is not
	 * associated with GloboNetwork, <code>null</code> will be returned.
	 * 
	 * @param networkId
	 * @return
	 */
	private Long getGloboNetworkVlanId(Long networkId) {
		if (networkId == null) {
			return null;
		}
		GloboNetworkNetworkVO vo = _globoNetworkNetworkDao.findByNetworkId(networkId);
		if (vo == null) {
			return null;
		}
		return vo.getGloboNetworkVlanId();
	}

	/**
	 * Get the number of vlan associate with {@code network}.
	 * 
	 * @param network
	 * @return
	 */
	private Integer getVlanNum(URI broadcastUri) {
		if (broadcastUri == null) {
			return null;
		}
		try {
			Integer vlanNum = Integer.valueOf(broadcastUri.getHost());
			return vlanNum;
		} catch (NumberFormatException nfe) {
			String msg = "Invalid Vlan number in broadcast URI " + broadcastUri;
			s_logger.error(msg);
			throw new CloudRuntimeException(msg, nfe);
		}
	}
	
	@Override
	@DB
	public GloboNetworkEnvironmentVO addGloboNetworkEnvironment(Long physicalNetworkId, String name, Long globoNetworkEnvironmentId) {
		
		if (name == null || name.trim().isEmpty()) {
			throw new InvalidParameterValueException("Invalid name: " + name);
		}
		
		// validate physical network and zone
		// Check if physical network exists
		PhysicalNetwork pNtwk = null;
		if (physicalNetworkId != null) {
			pNtwk = _physicalNetworkDao.findById(physicalNetworkId);
			if (pNtwk == null) {
				throw new InvalidParameterValueException(
						"Unable to find a physical network having the specified physical network id");
			}
		} else {
			throw new InvalidParameterValueException("Invalid physicalNetworkId: " + physicalNetworkId);
		}

		Long zoneId = pNtwk.getDataCenterId();
		
		// now, check if environment exists in GloboNetwork
		if (globoNetworkEnvironmentId != null) {
			Environment environment = getEnvironment(physicalNetworkId, globoNetworkEnvironmentId);
			if (environment == null) {
				throw new InvalidParameterValueException(
						"Unable to find in GloboNetwork an enviroment having the specified environment id");
			}
		} else {
			throw new InvalidParameterValueException("Invalid GloboNetwork environmentId: " + globoNetworkEnvironmentId);
		}
		
		
		// Check if there is a environment with same id or name in this zone.
		List<GloboNetworkEnvironmentVO> globoNetworkEnvironments = listGloboNetworkEnvironmentsFromDB(null, zoneId);
		for (GloboNetworkEnvironmentVO globoNetworkEnvironment: globoNetworkEnvironments) {
			if (globoNetworkEnvironment.getName().equalsIgnoreCase(name)) {
				throw new InvalidParameterValueException("GloboNetwork environment with name " + name + " already exists in zone " + zoneId);
			}
			if (globoNetworkEnvironment.getNapiEnvironmentId() == globoNetworkEnvironmentId) {
				throw new InvalidParameterValueException("GloboNetwork environment with environmentId " + globoNetworkEnvironmentId + " already exists in zoneId " + zoneId);
			}
		}
				
	    GloboNetworkEnvironmentVO napiEnvironmentVO = new GloboNetworkEnvironmentVO(physicalNetworkId, name, globoNetworkEnvironmentId);
	    _globoNetworkEnvironmentDao.persist(napiEnvironmentVO);
	    return napiEnvironmentVO;
	}

	@Override
	@DB
	public Host addGloboNetworkHost(Long physicalNetworkId, String username, String password, String url) {
		
		if (username == null || username.trim().isEmpty()) {
			throw new InvalidParameterValueException("Invalid username: " + username);
		}
		
		if (password == null || password.trim().isEmpty()) {
			throw new InvalidParameterValueException("Invalid password: " + password);
		}
		
		if (url == null || url.trim().isEmpty()) {
			throw new InvalidParameterValueException("Invalid url: " + url);
		}
		

		// validate physical network and zone
		// Check if physical network exists
		PhysicalNetwork pNtwk = null;
		if (physicalNetworkId != null) {
			pNtwk = _physicalNetworkDao.findById(physicalNetworkId);
			if (pNtwk == null) {
				throw new InvalidParameterValueException(
						"Unable to find a physical network having the specified physical network id");
			}
		} else {
			throw new InvalidParameterValueException("Invalid physicalNetworkId: " + physicalNetworkId);
		}

		final Long zoneId = pNtwk.getDataCenterId();

		final Map<String, String> params = new HashMap<String, String>();
		params.put("guid", "globonetwork-" + String.valueOf(zoneId));
		params.put("zoneId", String.valueOf(zoneId));
		params.put("name", Provider.GloboNetwork.getName());
		
		String readTimeout = _configDao.getValue(Config.GloboNetworkReadTimeout.key());
		String connectTimeout = _configDao.getValue(Config.GloboNetworkConnectionTimeout.key());
		String numberOfRetries = _configDao.getValue(Config.GloboNetworkNumberOfRetries.key());

		params.put("url", url);
		params.put("username", username);
		params.put("password", password);
		params.put("readTimeout", readTimeout);
		params.put("connectTimeout", connectTimeout);
		params.put("numberOfRetries", numberOfRetries);

		final Map<String, Object> hostDetails = new HashMap<String, Object>();
		hostDetails.putAll(params);
		
		Host host = Transaction.execute(new TransactionCallback<Host>() {

			@Override
			public Host doInTransaction(TransactionStatus status) {
				GloboNetworkResource resource = new GloboNetworkResource();

				try {
					resource.configure(Provider.GloboNetwork.getName(), hostDetails);
					
					Host host = _resourceMgr.addHost(zoneId, resource, resource.getType(),
							params);
					return host;
				} catch (ConfigurationException e) {
		            throw new CloudRuntimeException(e);
				}
			}
		});
       return host;
	}

	@Override
	public List<Class<?>> getCommands() {
		List<Class<?>> cmdList = new ArrayList<Class<?>>();
		cmdList.add(AddGloboNetworkVlanCmd.class);
		cmdList.add(AddNetworkViaGloboNetworkCmd.class);
		cmdList.add(AddGloboNetworkEnvironmentCmd.class);
		cmdList.add(ListGloboNetworkEnvironmentsCmd.class);
		cmdList.add(ListAllEnvironmentsFromGloboNetworkCmd.class);
		cmdList.add(RemoveGloboNetworkEnvironmentCmd.class);
		cmdList.add(AddGloboNetworkHostCmd.class);
		cmdList.add(AddGloboNetworkVipToAccountCmd.class);
		cmdList.add(ListGloboNetworkVipsCmd.class);
		cmdList.add(AddGloboNetworkRealToVipCmd.class);
		cmdList.add(DelGloboNetworkRealFromVipCmd.class);
		cmdList.add(GenerateUrlForEditingVipCmd.class);
		cmdList.add(RemoveGloboNetworkVipCmd.class);
		cmdList.add(ListGloboNetworkRealsCmd.class);
		return cmdList;
	}
	
	@Override
	public List<Environment> listAllEnvironmentsFromGloboNetwork(Long physicalNetworkId) {
		
		// validate physical network and zone
		// Check if physical network exists
		PhysicalNetwork pNtwk = null;
		if (physicalNetworkId != null) {
			pNtwk = _physicalNetworkDao.findById(physicalNetworkId);
			if (pNtwk == null) {
				throw new InvalidParameterValueException(
						"Unable to find a physical network having the specified physical network id");
			}
		} else {
			throw new InvalidParameterValueException("Invalid physicalNetworkId: " + physicalNetworkId);
		}
				
		Long zoneId = pNtwk.getDataCenterId();
		
		ListAllEnvironmentsFromGloboNetworkCommand cmd = new ListAllEnvironmentsFromGloboNetworkCommand();
		
		Answer answer = callCommand(cmd, zoneId);
		
		List<Environment> environments =  ((GloboNetworkAllEnvironmentResponse) answer).getEnvironmentList();
		return environments;
	}
	
	/**
	 * Get <code>Environment</code> object from environmentId.
	 * @param environmentId
	 * @return Return null if environment was not found.
	 */
	protected Environment getEnvironment(Long physicaNetworkId, Long environmentId) {
		if (environmentId == null) {
			return null;
		}
		
		Environment resultEnvironment = null;
		for (Environment environment : listAllEnvironmentsFromGloboNetwork(physicaNetworkId)) {
			if (environmentId.equals(environment.getId())) {
				resultEnvironment = environment;
				break;
			}
		}
		return resultEnvironment;
	}

	private void handleNetworkUnavaiableError(CloudstackGloboNetworkException e) {
		if (e.getNapiCode() == 116) {
			// If this is the return code, it means that the vlan/network no longer exists in GloboNetwork
			// and we should continue to remove it from CloudStack
			s_logger.warn("Inconsistency between CloudStack and GloboNetwork");
			return;
		} else {
			// Otherwise, there was a different error and we should abort the operation
			throw e;
		}
	}
	
	@Override
	public void removeNetworkFromGloboNetwork(Network network) {
		
		try {
			// Make sure the VLAN is valid
			this.getVlanInfoFromGloboNetwork(network);
		
			RemoveNetworkInGloboNetworkCommand cmd = new RemoveNetworkInGloboNetworkCommand();
			Long vlanId = getGloboNetworkVlanId(network.getId());
			cmd.setVlanId(vlanId);

			this.callCommand(cmd, network.getDataCenterId());
		} catch (CloudstackGloboNetworkException e) {
			handleNetworkUnavaiableError(e);
		}
	}
	
	@Override
	@DB
	public void deallocateVlanFromGloboNetwork(Network network) {

		try {
			GloboNetworkNetworkVO napiNetworkVO = _globoNetworkNetworkDao.findByNetworkId(network.getId());
			if (napiNetworkVO != null) {
				this.deallocateVlanFromGloboNetwork(network.getDataCenterId(), napiNetworkVO.getGloboNetworkVlanId());
				_globoNetworkNetworkDao.remove(napiNetworkVO.getId());
			}
			
		} catch (CloudstackGloboNetworkException e) {
			handleNetworkUnavaiableError(e);
		}
	}
	
	public void deallocateVlanFromGloboNetwork(Long zoneId, Long vlanId) {
		
		DeallocateVlanFromGloboNetworkCommand cmd = new DeallocateVlanFromGloboNetworkCommand();
		cmd.setVlanId(vlanId);
		
		this.callCommand(cmd, zoneId);
	}
	
	@Override
	public List<GloboNetworkEnvironmentVO> listGloboNetworkEnvironmentsFromDB(Long physicalNetworkId, Long zoneId) {
		List<GloboNetworkEnvironmentVO> globoNetworkEnvironmentsVOList;

		if (physicalNetworkId != null) {
			// Check if physical network exists
			PhysicalNetwork pNtwk = _physicalNetworkDao.findById(physicalNetworkId);
			if (pNtwk == null) {
				throw new InvalidParameterValueException(
						"Unable to find a physical network having the specified physical network id");
			}
			
			globoNetworkEnvironmentsVOList = _globoNetworkEnvironmentDao.listByPhysicalNetworkId(physicalNetworkId);

		} else if (zoneId != null) {
			// Check if zone exists
			DataCenter zone = _dcDao.findById(zoneId);
			if (zone == null) {
				throw new InvalidParameterValueException(
						"Specified zone id was not found");
			}

			globoNetworkEnvironmentsVOList = new ArrayList<GloboNetworkEnvironmentVO>();
			for (PhysicalNetworkVO physicalNetwork : _physicalNetworkDao.listByZone(zoneId)) {
				List<GloboNetworkEnvironmentVO> partialResult = _globoNetworkEnvironmentDao.listByPhysicalNetworkId(physicalNetwork.getId());
				if (partialResult != null) {
					globoNetworkEnvironmentsVOList.addAll(partialResult);
				}
			}
		} else {
			globoNetworkEnvironmentsVOList = _globoNetworkEnvironmentDao.listAll();
		}
		
		return globoNetworkEnvironmentsVOList;
	}

	@Override
	@DB
	public boolean removeGloboNetworkEnvironment(Long physicalNetworkId, Long globoNetworkEnvironmentId) {

        // Check if there are any networks in this GloboNetwork environment
        List<GloboNetworkNetworkVO> associationList = _globoNetworkNetworkDao.listByEnvironmentId(globoNetworkEnvironmentId);
        
        if (!associationList.isEmpty()) {
        	throw new InvalidParameterValueException("There are active networks on environment " + globoNetworkEnvironmentId + ". Please delete them before removing this environment.");
        }
        
		// Retrieve napiEnvironment from DB
		GloboNetworkEnvironmentVO globoNetworkEnvironment = _globoNetworkEnvironmentDao.findByPhysicalNetworkIdAndEnvironmentId(physicalNetworkId, globoNetworkEnvironmentId);
		
		if (globoNetworkEnvironment == null) {
			// No physical network/environment pair registered in the database.
			throw new InvalidParameterValueException("Unable to find a relationship between physical network=" + physicalNetworkId + " and GloboNetwork environment=" + globoNetworkEnvironmentId);
		}
		        
        boolean result = _globoNetworkEnvironmentDao.remove(globoNetworkEnvironment.getId());

		return result;
	}
	
	@Override
	public Vlan getVlanInfoFromGloboNetwork(Network network) {
		GetVlanInfoFromGloboNetworkCommand cmd = new GetVlanInfoFromGloboNetworkCommand();
		Long vlanId = getGloboNetworkVlanId(network.getId());
		cmd.setVlanId(vlanId);
	
		GloboNetworkVlanResponse response = (GloboNetworkVlanResponse) callCommand(cmd, network.getDataCenterId());
		
		Vlan vlan = new Vlan();
		vlan.setId(response.getVlanId());
		vlan.setName(response.getVlanName());
		vlan.setVlanNum(response.getVlanNum());
		vlan.setDescription(response.getVlanDescription());
		
		return vlan;
	}
	
	@Override
	public void registerNicInGloboNetwork(NicProfile nic, VirtualMachineProfile vm, Network network) {
		
		String msg = "Unable to register nic " + nic + " from VM " + vm + ".";
		if (vm == null || nic == null) {
			throw new CloudRuntimeException(msg + " Invalid nic, virtual machine or network.");
		}
		
		GloboNetworkNetworkVO globoNetworkNetworkVO = _globoNetworkNetworkDao.findByNetworkId(network.getId());
		if (globoNetworkNetworkVO == null) {
			throw new CloudRuntimeException(msg + " Could not obtain mapping for network in GloboNetwork.");
		}
		
		String equipmentGroup = _configDao.getValue(Config.GloboNetworkVmEquipmentGroup.key());
		if (equipmentGroup == null || "".equals(equipmentGroup)) {
			throw new CloudRuntimeException(msg + " Invalid equipment group for VM. Check your GloboNetwork global options.");
		}

		String equipmentModel = null;
		switch(vm.getType()) {
			case DomainRouter:
				equipmentModel = _configDao.getValue(Config.GloboNetworkModelVmDomainRouter.key());
				break;
			case ConsoleProxy:
				equipmentModel = _configDao.getValue(Config.GloboNetworkModelVmConsoleProxy.key());
				break;
			case SecondaryStorageVm:
				equipmentModel = _configDao.getValue(Config.GloboNetworkModelVmSecondaryStorageVm.key());
				break;
			case ElasticIpVm:
				equipmentModel = _configDao.getValue(Config.GloboNetworkModelVmElasticIpVm.key());
				break;
			case ElasticLoadBalancerVm:
				equipmentModel = _configDao.getValue(Config.GloboNetworkModelVmElasticLoadBalancerVm.key());
				break;
			case InternalLoadBalancerVm:
				equipmentModel = _configDao.getValue(Config.GloboNetworkModelVmInternalLoadBalancerVm.key());
				break;
			case UserBareMetal:
				equipmentModel = _configDao.getValue(Config.GloboNetworkModelVmUserBareMetal.key());
				break;
			default:
				equipmentModel = _configDao.getValue(Config.GloboNetworkModelVmUser.key());
				break;
		}
		if (equipmentModel == null) {
			throw new CloudRuntimeException(msg + " Invalid equipment model for VM of type " + vm.getType() + ". Check your GloboNetwork global options.");
		}
		
		RegisterEquipmentAndIpInGloboNetworkCommand cmd = new RegisterEquipmentAndIpInGloboNetworkCommand();
		cmd.setNicIp(nic.getIp4Address());
		cmd.setNicDescription("");
		cmd.setVmName(getEquipNameFromUuid(vm.getUuid()));
		cmd.setVlanId(globoNetworkNetworkVO.getGloboNetworkVlanId());
		cmd.setEnvironmentId(globoNetworkNetworkVO.getNapiEnvironmentId());
		cmd.setEquipmentGroupId(Long.valueOf(equipmentGroup));
		cmd.setEquipmentModelId(Long.valueOf(equipmentModel));
		
		Answer answer = this.callCommand(cmd, vm.getVirtualMachine().getDataCenterId());
		if (answer == null || !answer.getResult()) {
			msg = answer == null ? msg : answer.getDetails();
			throw new CloudRuntimeException(msg);
		}
	}
	
	private String getEquipNameFromUuid(String uuid) {
		String instanceNamePrefix = _configDao.getValue(Config.InstanceName.key());
		String equipName = "";
		if (instanceNamePrefix == null || instanceNamePrefix.equals("")) {
			equipName = uuid;
		} else {
			equipName = instanceNamePrefix + "-" + uuid;
		}
		return equipName;
	}

	@Override
	public void unregisterNicInGloboNetwork(NicProfile nic, VirtualMachineProfile vm) {
		
		String msg = "Unable to unregister nic " + nic + " from VM " + vm + ".";
		if (vm == null || nic == null) {
			throw new CloudRuntimeException(msg + " Invalid nic or virtual machine.");
		}
		
		String equipmentGroup = _configDao.getValue(Config.GloboNetworkVmEquipmentGroup.key());
		if (equipmentGroup == null) {
			throw new CloudRuntimeException(msg + " Invalid equipment group for VM. Check your GloboNetwork global options.");
		}
		
		UnregisterEquipmentAndIpInGloboNetworkCommand cmd = new UnregisterEquipmentAndIpInGloboNetworkCommand();
		cmd.setNicIp(nic.getIp4Address());
		cmd.setVmName(getEquipNameFromUuid(vm.getUuid()));

		GloboNetworkNetworkVO globoNetworkNetworkVO = _globoNetworkNetworkDao.findByNetworkId(nic.getNetworkId());
		if (globoNetworkNetworkVO != null) {
			cmd.setEnvironmentId(globoNetworkNetworkVO.getNapiEnvironmentId());
		}
		
		Answer answer = this.callCommand(cmd, vm.getVirtualMachine().getDataCenterId());
		if (answer == null || !answer.getResult()) {
			msg = answer == null ? msg : answer.getDetails();
			throw new CloudRuntimeException(msg);
		}
	}

	@Override
	public GloboNetworkVipAccVO addGloboNetworkVipToAcc(Long globoNetworkVipId, Long networkId) {

		Account caller = CallContext.current().getCallingAccount();
		Network network = null;
		if (networkId != null) {
			network = _ntwkDao.findById(networkId);
			if (network == null) {
				throw new InvalidParameterValueException(
						"Unable to find a network having the specified network id");
			}
		} else {
			throw new InvalidParameterValueException("Invalid networkId: " + networkId);
		}
        // Perform account permission check on network
        _accountMgr.checkAccess(caller, AccessType.UseNetwork, false, network);
		
        GetVipInfoFromGloboNetworkCommand cmd = new GetVipInfoFromGloboNetworkCommand();
		cmd.setVipId(globoNetworkVipId);
		Answer answer = this.callCommand(cmd, network.getDataCenterId());
		String msg = "Could not validate VIP id with GloboNetwork";
		if (answer == null || !answer.getResult()) {
			msg = answer == null ? msg : answer.getDetails();
			throw new CloudRuntimeException(msg);
		}
		
		// TODO Remove accountId
		Long accountId = network.getAccountId();
		GloboNetworkVipAccVO globoNetworkVipAcc = _globoNetworkVipAccDao.findGloboNetworkVipAcc(globoNetworkVipId, accountId, networkId);
		if (globoNetworkVipAcc != null) {
			// Already exists, continue
			s_logger.info("Association between VIP " + globoNetworkVipId + " and network " + networkId + " already exists");
		} else {
			globoNetworkVipAcc = new GloboNetworkVipAccVO(globoNetworkVipId, accountId, networkId);
			_globoNetworkVipAccDao.persist(globoNetworkVipAcc);
		}

	    return globoNetworkVipAcc;
	}

	@Override
	public void associateNicToVip(Long vipId, Nic nic) {
		VMInstanceVO vm = _vmDao.findById(nic.getInstanceId());
		if (vm == null) {
			throw new CloudRuntimeException("There is no VM that belongs to nic " + nic);
		}
		
		GloboNetworkVipAccVO globoNetworkVipVO = _globoNetworkVipAccDao.findGloboNetworkVip(vipId, nic.getNetworkId());
		if (globoNetworkVipVO == null) {
			throw new InvalidParameterValueException("Vip " + vipId + " is not associated with Cloudstack");
		}
		
		Network network = _ntwkDao.findById(nic.getNetworkId());
		if (network == null) {
			throw new InvalidParameterValueException("Network " + nic.getNetworkId() + " doesn't exist in Cloudstack");
		}
		
		AddAndEnableRealInGloboNetworkCommand cmd = new AddAndEnableRealInGloboNetworkCommand();
		cmd.setEquipName(getEquipNameFromUuid(vm.getUuid()));
		cmd.setIp(nic.getIp4Address());
		cmd.setVipId(vipId);
		Answer answer = callCommand(cmd, network.getDataCenterId());
		if (answer == null || !answer.getResult()) {
			throw new CloudRuntimeException("Error associating nic " + nic +
					" to vip " + vipId + ": " + (answer == null ? null : answer.getDetails()));
		}
	}

	@Override
	public void disassociateNicFromVip(Long vipId, Nic nic) {
		DisableAndRemoveRealInGloboNetworkCommand cmd = new DisableAndRemoveRealInGloboNetworkCommand();
		VMInstanceVO vm = _vmDao.findById(nic.getInstanceId());
		if (vm == null) {
			throw new CloudRuntimeException("There is no VM that belongs to nic " + nic);
		}
		cmd.setEquipName(getEquipNameFromUuid(vm.getUuid()));
		cmd.setIp(nic.getIp4Address());
		cmd.setVipId(vipId);
		Network network = _ntwkDao.findById(nic.getNetworkId());
		Answer answer = callCommand(cmd, network.getDataCenterId());
		if (answer == null || !answer.getResult()) {
			throw new CloudRuntimeException("Error removing nic " + nic +
					" from vip " + vipId + ": " + (answer == null ? null : answer.getDetails()));
		}
	}

	@Override
	public List<GloboNetworkVipResponse> listGloboNetworkVips(Long projectId) {

		Account caller = CallContext.current().getCallingAccount();
		List<Long> permittedAccounts = new ArrayList<Long>();
		
        permittedAccounts.add(caller.getId());
        
		if (projectId != null) {
            Project project = _projectMgr.getProject(projectId);
            if (project == null) {
                throw new InvalidParameterValueException("Unable to find project by specified id");
            }
            if (!_projectMgr.canAccessProjectAccount(caller, project.getProjectAccountId())) {
                // getProject() returns type ProjectVO.
                InvalidParameterValueException ex = new InvalidParameterValueException("Account " + caller + " cannot access specified project id");
                ex.addProxyObject(project.getUuid(), "projectId");
                throw ex;
            }
            
            permittedAccounts.add(project.getProjectAccountId());
		}
		
		// FIXME Improve this search by creating a custom criteria
		List<DataCenterVO> zones = _dcDao.listAllZones();
		List<NetworkVO> allowedNetworks = new ArrayList<NetworkVO>();
		for (DataCenterVO zone : zones) {
			for (Long accountId : permittedAccounts) {
				allowedNetworks.addAll(_ntwkDao.listNetworksByAccount(accountId, zone.getId(), Network.GuestType.Shared, false));
			}
		}
		
		List<Long> networkIds = new ArrayList<Long>();
		for (NetworkVO networkVO : allowedNetworks) {
			networkIds.add(networkVO.getId());
		}
		
		if (networkIds.isEmpty()) {
			return new ArrayList<GloboNetworkVipResponse>();
		}
		
		// Get all vip Ids related to networks
		List<GloboNetworkVipAccVO> globoNetworkVipAccList = _globoNetworkVipAccDao.listByNetworks(networkIds);
		
		Map<Long, GloboNetworkVipResponse> vips = new HashMap<Long, GloboNetworkVipResponse>();
		for (GloboNetworkVipAccVO globoNetworkVipAcc : globoNetworkVipAccList) {

			Network network = _ntwkDao.findById(globoNetworkVipAcc.getNetworkId());
			
			GloboNetworkVipResponse vip;
			
			if (vips.get(globoNetworkVipAcc.getNapiVipId()) == null) {
				
				// Vip is not in the returning map yet, get all info from GloboNetwork
				GetVipInfoFromGloboNetworkCommand cmd = new GetVipInfoFromGloboNetworkCommand();
				cmd.setVipId(globoNetworkVipAcc.getNapiVipId());
				Answer answer = this.callCommand(cmd, network.getDataCenterId());
				String msg = "Could not list VIPs from GloboNetwork";
				if (answer == null || !answer.getResult()) {
					msg = answer == null ? msg : answer.getDetails();
					throw new CloudRuntimeException(msg);
				}
				vip =  ((GloboNetworkVipResponse) answer);
				
				vips.put(globoNetworkVipAcc.getNapiVipId(), vip);
				
			} else {
				// Vip is already in the returning map
				vip = vips.get(globoNetworkVipAcc.getNapiVipId());
			}
			
			if (vip.getNetworkIds() == null) {
				vip.setNetworkIds(new ArrayList<String>());
			}
			vip.getNetworkIds().add(network.getUuid());
			
		}
		return new ArrayList<GloboNetworkVipResponse>(vips.values());
	}

	@Override
	public String generateUrlForEditingVip(Long vipId, Network network) {
		
		GenerateUrlForEditingVipCommand cmd = new GenerateUrlForEditingVipCommand(vipId, GloboNetworkVIPServerUrl.value());
		Answer answer = callCommand(cmd, network.getDataCenterId());
		String msg = "Could not list VIPs from GloboNetwork";
		if (answer == null || !answer.getResult()) {
			msg = answer == null ? msg : answer.getDetails();
			throw new CloudRuntimeException(msg);
		}
		return answer.getDetails();
	}
	
	@Override
	public void removeGloboNetworkVip(Long napiVipId) {

		Account caller = CallContext.current().getCallingAccount();
		
		List<GloboNetworkVipAccVO> globoNetworkVipList = _globoNetworkVipAccDao.findByVipId(napiVipId);
		
		if (globoNetworkVipList == null || globoNetworkVipList.isEmpty()) {
			throw new InvalidParameterValueException(
					"Unable to find an association for VIP " + napiVipId);
		}
		
		Network network = null;
		for (GloboNetworkVipAccVO globoNetworkVipAccVO : globoNetworkVipList) {
			network = _ntwkDao.findById(globoNetworkVipAccVO.getNetworkId());
			if (network == null) {
				throw new InvalidParameterValueException(
						"Unable to find a network having the specified network id");
			}
			// Perform account permission check on network
	        _accountMgr.checkAccess(caller, AccessType.UseNetwork, false, network);
	     
	        _globoNetworkVipAccDao.remove(globoNetworkVipAccVO.getId());
	    
		}
		
		RemoveVipFromGloboNetworkCommand cmd = new RemoveVipFromGloboNetworkCommand();
		cmd.setVipId(napiVipId);
		
		Answer answer = this.callCommand(cmd, network.getDataCenterId());
		
		String msg = "Could not remove VIP " + napiVipId + " from GloboNetwork";
		if (answer == null || !answer.getResult()) {
			msg = answer == null ? msg : answer.getDetails();
			throw new CloudRuntimeException(msg);
		}
	}
	
	@Override
	public List<GloboNetworkVipResponse.Real> listGloboNetworkReals(Long vipId) {
		if (vipId == null) {
			throw new InvalidParameterValueException("Invalid VIP id");
		}
		
		List<GloboNetworkVipAccVO> globoNetworkVips = _globoNetworkVipAccDao.findByVipId(vipId);

		if (globoNetworkVips == null) {
			throw new CloudRuntimeException("Could not find VIP " + vipId);
		}
		
		List<GloboNetworkVipResponse.Real> reals = new ArrayList<GloboNetworkVipResponse.Real>();
		
		if (globoNetworkVips.isEmpty()) {
			return reals;
		}
		
		// We need a network to call commands, any network associated to this VIP will do
		Network network = _ntwkDao.findById(globoNetworkVips.get(0).getNetworkId());

		if (network == null) {
			throw new CloudRuntimeException("Could not find network with networkId " + globoNetworkVips.get(0).getNetworkId());
		}
		
		GetVipInfoFromGloboNetworkCommand cmd = new GetVipInfoFromGloboNetworkCommand();
		cmd.setVipId(vipId);
		Answer answer = this.callCommand(cmd, network.getDataCenterId());
		String msg = "Could not find VIP from GloboNetwork";
		if (answer == null || !answer.getResult()) {
			msg = answer == null ? msg : answer.getDetails();
			throw new CloudRuntimeException(msg);
		}
		GloboNetworkVipResponse vip =  ((GloboNetworkVipResponse) answer);
		
		for (Real real : vip.getReals()) {
			for (GloboNetworkVipAccVO globoNetworkVipVO : globoNetworkVips) {
				network = _ntwkDao.findById(globoNetworkVipVO.getNetworkId());
				
				if(!NetUtils.isIpWithtInCidrRange(real.getIp(), network.getCidr())) {
					// If real's IP is not within network range, skip it
					continue;
				}
				
				Nic nic = _nicDao.findByIp4AddressAndNetworkId(real.getIp(), network.getId());
				if (nic != null) {
					real.setNic(String.valueOf(nic.getId()));
					real.setNetwork(network.getName());
				
					// User VM name rather than UUID
					VMInstanceVO userVM = _vmDao.findByUuid(real.getVmName());
					if (userVM != null) {
						real.setVmName(userVM.getHostName());
					}
				}
			}
			
			reals.add(real);
		}
			
		return reals;
	}
}
