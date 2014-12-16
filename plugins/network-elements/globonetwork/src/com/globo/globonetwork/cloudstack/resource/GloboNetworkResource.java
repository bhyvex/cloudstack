/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.globo.globonetwork.cloudstack.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.naming.ConfigurationException;

import org.apache.log4j.Logger;

import com.cloud.agent.IAgentControl;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import com.cloud.agent.api.MaintainAnswer;
import com.cloud.agent.api.MaintainCommand;
import com.cloud.agent.api.PingCommand;
import com.cloud.agent.api.ReadyAnswer;
import com.cloud.agent.api.ReadyCommand;
import com.cloud.agent.api.StartupCommand;
import com.cloud.host.Host;
import com.cloud.host.Host.Type;
import com.cloud.resource.ServerResource;
import com.cloud.utils.component.ManagerBase;
import com.cloud.utils.net.Ip4Address;
import com.cloud.utils.net.NetUtils;
import com.globo.globonetwork.client.exception.GloboNetworkErrorCodeException;
import com.globo.globonetwork.client.exception.GloboNetworkException;
import com.globo.globonetwork.client.http.HttpXMLRequestProcessor;
import com.globo.globonetwork.client.model.Environment;
import com.globo.globonetwork.client.model.Equipment;
import com.globo.globonetwork.client.model.IPv4Network;
import com.globo.globonetwork.client.model.Ip;
import com.globo.globonetwork.client.model.Vlan;
import com.globo.globonetwork.cloudstack.commands.ActivateNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.CreateNewVlanInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.DeallocateVlanFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GetVlanInfoFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.GloboNetworkErrorAnswer;
import com.globo.globonetwork.cloudstack.commands.ListAllEnvironmentsFromGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.RegisterEquipmentAndIpInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.RemoveNetworkInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.UnregisterEquipmentAndIpInGloboNetworkCommand;
import com.globo.globonetwork.cloudstack.commands.ValidateNicInVlanCommand;
import com.globo.globonetwork.cloudstack.response.GloboNetworkAllEnvironmentResponse;
import com.globo.globonetwork.cloudstack.response.GloboNetworkVlanResponse;

public class GloboNetworkResource extends ManagerBase implements ServerResource {
    private String _zoneId;

    private String _guid;

    private String _name;

    private String _username;

    private String _url;

    private String _password;

    protected HttpXMLRequestProcessor _globoNetworkApi;

    private static final Logger s_logger = Logger.getLogger(GloboNetworkResource.class);

    private static final long NETWORK_TYPE = 6; // Rede invalida de equipamentos

    private static final Long EQUIPMENT_TYPE = 10L;

    @Override
    public boolean configure(String name, Map<String, Object> params) throws ConfigurationException {

        try {
            _zoneId = (String)params.get("zoneId");
            if (_zoneId == null) {
                throw new ConfigurationException("Unable to find zone");
            }

            _guid = (String)params.get("guid");
            if (_guid == null) {
                throw new ConfigurationException("Unable to find guid");
            }

            _name = (String)params.get("name");
            if (_name == null) {
                throw new ConfigurationException("Unable to find name");
            }

            _url = (String)params.get("url");
            if (_url == null) {
                throw new ConfigurationException("Unable to find url");
            }

            _username = (String)params.get("username");
            if (_username == null) {
                throw new ConfigurationException("Unable to find username");
            }

            _password = (String)params.get("password");
            if (_password == null) {
                throw new ConfigurationException("Unable to find password");
            }

            _globoNetworkApi = new HttpXMLRequestProcessor(_url, _username, _password);

            if (params.containsKey("readTimeout")) {
                _globoNetworkApi.setReadTimeout(Integer.valueOf((String)params.get("readTimeout")));
            }

            if (params.containsKey("connectTimeout")) {
                _globoNetworkApi.setConnectTimeout(Integer.valueOf((String)params.get("connectTimeout")));
            }

            if (params.containsKey("numberOfRetries")) {
                _globoNetworkApi.setNumberOfRetries(Integer.valueOf((String)params.get("numberOfRetries")));
            }

            return true;
        } catch (NumberFormatException e) {
            s_logger.error("Invalid number in configuration parameters", e);
            throw new ConfigurationException("Invalid number in configuration parameters: " + e);
        }
    }

    @Override
    public boolean start() {
        return true;
    }

    @Override
    public boolean stop() {
        return true;
    }

    @Override
    public Type getType() {
        return Host.Type.L2Networking;
    }

    @Override
    public StartupCommand[] initialize() {
        StartupCommand cmd = new StartupCommand(getType());
        cmd.setName(_name);
        cmd.setGuid(_guid);
        cmd.setDataCenter(_zoneId);
        cmd.setPod("");
        cmd.setPrivateIpAddress("");
        cmd.setStorageIpAddress("");
        cmd.setVersion(GloboNetworkResource.class.getPackage().getImplementationVersion());
        return new StartupCommand[] {cmd};
    }

    @Override
    public PingCommand getCurrentStatus(long id) {
        return new PingCommand(getType(), id);
    }

    @Override
    public void disconnected() {
    }

    @Override
    public IAgentControl getAgentControl() {
        return null;
    }

    @Override
    public void setAgentControl(IAgentControl agentControl) {
    }

    @Override
    public Answer executeRequest(Command cmd) {
        if (cmd instanceof ReadyCommand) {
            return new ReadyAnswer((ReadyCommand)cmd);
        } else if (cmd instanceof MaintainCommand) {
            return new MaintainAnswer((MaintainCommand)cmd);
        } else if (cmd instanceof GetVlanInfoFromGloboNetworkCommand) {
            return execute((GetVlanInfoFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof ValidateNicInVlanCommand) {
            return execute((ValidateNicInVlanCommand)cmd);
        } else if (cmd instanceof CreateNewVlanInGloboNetworkCommand) {
            return execute((CreateNewVlanInGloboNetworkCommand)cmd);
        } else if (cmd instanceof ActivateNetworkCommand) {
            return execute((ActivateNetworkCommand)cmd);
        } else if (cmd instanceof ListAllEnvironmentsFromGloboNetworkCommand) {
            return execute((ListAllEnvironmentsFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof RemoveNetworkInGloboNetworkCommand) {
            return execute((RemoveNetworkInGloboNetworkCommand)cmd);
        } else if (cmd instanceof DeallocateVlanFromGloboNetworkCommand) {
            return execute((DeallocateVlanFromGloboNetworkCommand)cmd);
        } else if (cmd instanceof RegisterEquipmentAndIpInGloboNetworkCommand) {
            return execute((RegisterEquipmentAndIpInGloboNetworkCommand)cmd);
        } else if (cmd instanceof UnregisterEquipmentAndIpInGloboNetworkCommand) {
            return execute((UnregisterEquipmentAndIpInGloboNetworkCommand)cmd);
        }
        return Answer.createUnsupportedCommandAnswer(cmd);
    }

    private Answer handleGloboNetworkException(Command cmd, GloboNetworkException e) {
        if (e instanceof GloboNetworkErrorCodeException) {
            GloboNetworkErrorCodeException ex = (GloboNetworkErrorCodeException)e;
            s_logger.error("Error accessing GloboNetwork: " + ex.getCode() + " - " + ex.getDescription(), ex);
            return new GloboNetworkErrorAnswer(cmd, ex.getCode(), ex.getDescription());
        } else {
            s_logger.error("Generic error accessing GloboNetwork", e);
            return new Answer(cmd, false, e.getMessage());
        }
    }

    /**
     * Validate if Nic ip and vlan number belongs to GloboNetwork VlanId
     * @param cmd
     * @return
     */
    public Answer execute(ValidateNicInVlanCommand cmd) {
        try {
            Vlan vlan = _globoNetworkApi.getVlanAPI().getById(cmd.getVlanId());
            List<IPv4Network> networks = vlan.getIpv4Networks();
            if (networks.isEmpty() || !networks.get(0).getActive()) {
                return new Answer(cmd, false, "No active networks found in VlanId " + cmd.getVlanId());
            }

            IPv4Network network = networks.get(0);
            String networkAddress = network.getOct1() + "." + network.getOct2() + "." + network.getOct3() + "." + network.getOct4();
            long ipLong = NetUtils.ip2Long(cmd.getNicIp());
            String netmask = network.getMaskOct1() + "." + network.getMaskOct2() + "." + network.getMaskOct3() + "." + network.getMaskOct4();
            long cidrSize = NetUtils.getCidrSize(netmask);
            String ipRange[] = NetUtils.getIpRangeFromCidr(networkAddress, cidrSize);
            if (!(ipLong >= NetUtils.ip2Long(ipRange[0]) && ipLong <= NetUtils.ip2Long(ipRange[1]))) {
                return new Answer(cmd, false, "Nic IP " + cmd.getNicIp() + " does not belong to network " + networkAddress + " in vlanId " + cmd.getVlanId());
            }
            return new Answer(cmd);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(GetVlanInfoFromGloboNetworkCommand cmd) {
        try {
            Vlan vlan = _globoNetworkApi.getVlanAPI().getById(cmd.getVlanId());
            return createResponse(vlan, cmd);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(CreateNewVlanInGloboNetworkCommand cmd) {
        try {
            Vlan vlan = _globoNetworkApi.getVlanAPI().allocateWithoutNetwork(cmd.getGloboNetworkEnvironmentId(), cmd.getVlanName(), cmd.getVlanDescription());

            /*Network network = */_globoNetworkApi.getNetworkAPI().addNetworkIpv4(vlan.getId(), Long.valueOf(NETWORK_TYPE), null);

            // Bug in GloboNetworkApi: I need to have a second call to get networkid
            vlan = _globoNetworkApi.getVlanAPI().getById(vlan.getId());
            return createResponse(vlan, cmd);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(ActivateNetworkCommand cmd) {
        try {
            _globoNetworkApi.getNetworkAPI().createNetworks(cmd.getNetworkId(), cmd.getVlanId());
            return new Answer(cmd, true, "Network created");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(ListAllEnvironmentsFromGloboNetworkCommand cmd) {
        try {
            List<Environment> apiEnvironmentList = _globoNetworkApi.getEnvironmentAPI().listAll();

            List<GloboNetworkAllEnvironmentResponse.Environment> environmentList = new ArrayList<GloboNetworkAllEnvironmentResponse.Environment>(apiEnvironmentList.size());
            for (Environment apiEnvironment : apiEnvironmentList) {
                GloboNetworkAllEnvironmentResponse.Environment environment = new GloboNetworkAllEnvironmentResponse.Environment();
                environment.setId(apiEnvironment.getId());
                environment.setDcDivisionName(apiEnvironment.getDcDivisionName());
                environment.setL3GroupName(apiEnvironment.getL3GroupName());
                environment.setLogicalEnvironmentName(apiEnvironment.getLogicalEnvironmentName());
                environmentList.add(environment);
            }

            return new GloboNetworkAllEnvironmentResponse(cmd, environmentList);
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(RemoveNetworkInGloboNetworkCommand cmd) {
        try {
            _globoNetworkApi.getVlanAPI().remove(cmd.getVlanId());

            return new Answer(cmd, true, "Network removed");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(DeallocateVlanFromGloboNetworkCommand cmd) {
        try {
            _globoNetworkApi.getVlanAPI().deallocate(cmd.getVlanId());
            return new Answer(cmd, true, "Vlan deallocated");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(RegisterEquipmentAndIpInGloboNetworkCommand cmd) {
        try {
            Equipment equipment = _globoNetworkApi.getEquipmentAPI().listByName(cmd.getVmName());
            if (equipment == null) {
                s_logger.info("Registering virtualmachine " + cmd.getVmName() + " in GloboNetwork");
                // Equipment (VM) does not exist, create it
                equipment = _globoNetworkApi.getEquipmentAPI().insert(cmd.getVmName(), EQUIPMENT_TYPE, cmd.getEquipmentModelId(), cmd.getEquipmentGroupId());
            }

            Vlan vlan = _globoNetworkApi.getVlanAPI().getById(cmd.getVlanId());

            // Make sure this vlan has one IPv4 network associated to it
            if (vlan.getIpv4Networks().size() == 0) {
                return new Answer(cmd, false, "No IPv4 networks in this vlan");
            } else if (vlan.getIpv4Networks().size() > 1) {
                return new Answer(cmd, false, "Multiple IPv4 networks in this vlan");
            }
            Long networkId = vlan.getIpv4Networks().get(0).getId();

            Ip ip = _globoNetworkApi.getIpAPI().findByIpAndEnvironment(cmd.getNicIp(), cmd.getEnvironmentId());
            if (ip == null) {
                // Doesn't exist, create it
                ip = _globoNetworkApi.getIpAPI().saveIpv4(cmd.getNicIp(), equipment.getId(), cmd.getNicDescription(), networkId);
            } else {
                ip = _globoNetworkApi.getIpAPI().getIpv4(ip.getId());
                if (!ip.getEquipments().contains(cmd.getVmName())) {
                    _globoNetworkApi.getIpAPI().assocIpv4(ip.getId(), equipment.getId(), networkId);
                }
            }

            if (ip == null) {
                return new Answer(cmd, false, "Could not register NIC in GloboNetwork");
            }

            return new Answer(cmd, true, "NIC " + cmd.getNicIp() + " registered successfully in GloboNetwork");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    public Answer execute(UnregisterEquipmentAndIpInGloboNetworkCommand cmd) {
        try {
            Equipment equipment = _globoNetworkApi.getEquipmentAPI().listByName(cmd.getVmName());
            if (equipment == null) {
                s_logger.warn("VM was removed from GloboNetwork before being destroyed in Cloudstack. This is not critical, logging inconsistency: VM UUID " + cmd.getVmName());
                return new Answer(cmd);
            }

            if (cmd.getEnvironmentId() != null && cmd.getNicIp() != null) {
                Ip ip = _globoNetworkApi.getIpAPI().findByIpAndEnvironment(cmd.getNicIp(), cmd.getEnvironmentId());
                if (ip == null) {
                    // Doesn't exist, ignore
                    s_logger.warn("IP was removed from GloboNetwork before being destroyed in Cloudstack. This is not critical, logging inconsistency: IP " + cmd.getNicIp());
                } else {
                    _globoNetworkApi.getEquipmentAPI().removeIP(equipment.getId(), ip.getId());
                }
            }

            // if there are no more IPs in equipment, remove it.
            List<Ip> ipList = _globoNetworkApi.getIpAPI().findIpsByEquipment(equipment.getId());
            if (ipList.size() == 0) {
                _globoNetworkApi.getEquipmentAPI().delete(equipment.getId());
            }

            return new Answer(cmd, true, "NIC " + cmd.getNicIp() + " deregistered successfully in GloboNetwork");
        } catch (GloboNetworkException e) {
            return handleGloboNetworkException(cmd, e);
        }
    }

    private Answer createResponse(Vlan vlan, Command cmd) {

        if (vlan.getIpv4Networks().isEmpty()) {
            // Error code 116 from GloboNetwork: 116 : VlanNaoExisteError,
            return new GloboNetworkErrorAnswer(cmd, 116, "No networks in this VLAN");
        }

        IPv4Network ipv4Network = vlan.getIpv4Networks().get(0);

        String vlanName = vlan.getName();
        String vlanDescription = vlan.getDescription();
        Long vlanId = vlan.getId();
        Long vlanNum = vlan.getVlanNum();
        Ip4Address mask = new Ip4Address(ipv4Network.getMaskOct1() + "." + ipv4Network.getMaskOct2() + "." + ipv4Network.getMaskOct3() + "." + ipv4Network.getMaskOct4());
        Ip4Address networkAddress = new Ip4Address(ipv4Network.getOct1() + "." + ipv4Network.getOct2() + "." + ipv4Network.getOct3() + "." + ipv4Network.getOct4());
        return new GloboNetworkVlanResponse(cmd, vlanId, vlanName, vlanDescription, vlanNum, networkAddress, mask, ipv4Network.getId(), ipv4Network.getActive());
    }

}
