package br.com.devcase.webmonitor.persistence.dao;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;
import java.util.Map;

import javax.transaction.Transactional;

import org.springframework.stereotype.Repository;

import br.com.devcase.webmonitor.persistence.domain.MonitoredResource;
import dwf.persistence.dao.BaseDAOImpl;
import dwf.persistence.dao.DefaultQueryBuilder;
import dwf.persistence.dao.QueryBuilder;
import dwf.persistence.dao.QueryReturnType;
import dwf.utils.ParsedMap;

@Repository("monitoredResourceDAO")
@Transactional
public class MonitoredResourceDAOImpl extends BaseDAOImpl<MonitoredResource> implements MonitoredResourceDAO{

	public MonitoredResourceDAOImpl() {
		super(MonitoredResource.class);
	}

	@Override
	protected QueryBuilder createQueryBuilder() {
		return new DefaultQueryBuilder(this) {
			
			

			@Override
			protected void appendJoins(ParsedMap filter, QueryReturnType<?> returnType, Map<String, Object> params,
					StringBuilder query, String domainAlias) {
				super.appendJoins(filter, returnType, params, query, domainAlias);
				if(Boolean.TRUE.equals(filter.getBoolean("pending"))) {
					query.append(" join ").append(domainAlias).append(".scheduledTimes as scheduledTime ");
				}
			}

			@Override
			protected void appendConditions(ParsedMap filter, QueryReturnType<?> returnType, Map<String, Object> params,
					StringBuilder query, String alias) {
				super.appendConditions(filter, returnType, params, query, alias);
				if(Boolean.TRUE.equals(filter.getBoolean("pending"))) {
					query.append(" and (");
						query.append("(");
							query.append(" (").append(alias).append(".nextHealthCheck is null) ");
							query.append(" or ");
							query.append(" (").append(alias).append(".nextHealthCheck <= :now) ");
						query.append(") and (");
							query.append("scheduledTime.timeAsDate = :scheduledTime ");
							query.append(" and ");
							query.append("scheduledTime.dayOfWeek = :scheduledDayOfWeek ");
						query.append(")");
					query.append(")");
					
					params.put("now", new Date());
					LocalTime timeNow = LocalTime.now();
					timeNow = LocalTime.of(timeNow.getHour(), timeNow.getMinute() - (timeNow.getMinute() % 30));
					params.put("scheduledTime", new Date(timeNow.toSecondOfDay() * 1000));
					params.put("scheduledDayOfWeek", DayOfWeek.from(LocalDate.now()));
				}
			}
			
		};
	}

	
}