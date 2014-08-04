package com.globo.globonetwork.cloudstack.dao;

import java.util.List;

import javax.ejb.Local;

import org.springframework.stereotype.Component;

import com.cloud.utils.db.DB;
import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.SearchCriteria.Op;
import com.globo.globonetwork.cloudstack.GloboNetworkVipAccVO;

@Component
@Local(value = GloboNetworkVipAccDao.class)
@DB
public class GloboNetworkVipAccDaoImpl extends
		GenericDaoBase<GloboNetworkVipAccVO, Long> implements
		GloboNetworkVipAccDao {

	final SearchBuilder<GloboNetworkVipAccVO> allParamsSearch;
	
	final SearchBuilder<GloboNetworkVipAccVO> networkSearch;

	final SearchBuilder<GloboNetworkVipAccVO> byNetwork;

	final SearchBuilder<GloboNetworkVipAccVO> byVip;
	
	final SearchBuilder<GloboNetworkVipAccVO> byNetworkAndVip;

	protected GloboNetworkVipAccDaoImpl() {
		super();

		allParamsSearch = createSearchBuilder();
		allParamsSearch.and("napi_vip_id", allParamsSearch.entity().getNapiVipId(), Op.EQ);
		allParamsSearch.and("account_id", allParamsSearch.entity().getAccountId(), Op.EQ);
		allParamsSearch.and("network_id", allParamsSearch.entity().getNetworkId(), Op.EQ);
		allParamsSearch.done();
		
		networkSearch = createSearchBuilder();
		networkSearch.and("network_id", networkSearch.entity().getNetworkId(), Op.IN);
		networkSearch.done();

		byNetwork = createSearchBuilder();
		byNetwork.and("network_id", byNetwork.entity().getNetworkId(), Op.EQ);
		byNetwork.done();
		
		byVip = createSearchBuilder();
		byVip.and("napi_vip_id", byVip.entity().getNapiVipId(), Op.EQ);
		byVip.done();
		
		byNetworkAndVip = createSearchBuilder();
		byNetworkAndVip.and("network_id", byNetworkAndVip.entity().getNetworkId(), Op.EQ);
		byNetworkAndVip.and("napi_vip_id", byNetworkAndVip.entity().getNapiVipId(), Op.EQ);
		byNetworkAndVip.done();
	}

	@Override
	public GloboNetworkVipAccVO findGloboNetworkVipAcc(long vipId,
			long accountId, long networkId) {
		SearchCriteria<GloboNetworkVipAccVO> sc = allParamsSearch.create();
		sc.setParameters("napi_vip_id", vipId);
		sc.setParameters("account_id", accountId);
		sc.setParameters("network_id", networkId);
		return findOneBy(sc);
	}
	
	public List<GloboNetworkVipAccVO> listByNetworks(List<Long> networkIdList) {
		SearchCriteria<GloboNetworkVipAccVO> sc = networkSearch.create();
		sc.setParameters("network_id", networkIdList.toArray(new Object[networkIdList.size()]));
		return listBy(sc);
	}

	@Override
	public List<GloboNetworkVipAccVO> findByNetwork(long networkId) {
		SearchCriteria<GloboNetworkVipAccVO> sc = byNetwork.create();
		sc.setParameters("network_id", networkId);
		return listBy(sc);
	}

	@Override
	public GloboNetworkVipAccVO findGloboNetworkVip(long vipId, long networkId) {
		SearchCriteria<GloboNetworkVipAccVO> sc = byNetworkAndVip.create();
		sc.setParameters("napi_vip_id", vipId);
		sc.setParameters("network_id", networkId);
		return findOneBy(sc);
	}

	@Override
	public List<GloboNetworkVipAccVO> findByVipId(long vipId) {
		SearchCriteria<GloboNetworkVipAccVO> sc = byVip.create();
		sc.setParameters("napi_vip_id", vipId);
		return listBy(sc);
	}

}
