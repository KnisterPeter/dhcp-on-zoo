/*
 * Copyright 2009 Jagornet Technologies, LLC.  All Rights Reserved.
 *
 * This software is the proprietary information of Jagornet Technologies, LLC. 
 * Use is subject to license terms.
 *
 */

/*
 *   This file JdbcLeaseManager is part of DHCPv6.
 *
 *   DHCPv6 is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   DHCPv6 is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with DHCPv6.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.jagornet.dhcpv6.db;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcDaoSupport;

import com.jagornet.dhcpv6.server.request.binding.Range;
import com.jagornet.dhcpv6.util.Util;

/**
 * The JdbcLeaseManager implementation class for the IaManager interface.
 * This is the main database access class for handling client bindings.
 * 
 * @author A. Gregory Rabil
 */
public class JdbcLeaseManager extends SimpleJdbcDaoSupport implements IaManager
{	
	private static Logger log = LoggerFactory.getLogger(JdbcLeaseManager.class);

	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#createIA(com.jagornet.dhcpv6.db.IdentityAssoc)
	 */
	public void createIA(IdentityAssoc ia) {
		if (ia != null) {
			List<DhcpLease> leases = toDhcpLeases(ia);
			if ((leases != null) && !leases.isEmpty()) {
				for (final DhcpLease lease : leases) {
					insertDhcpLease(lease);
				}
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#updateIA(com.jagornet.dhcpv6.db.IdentityAssoc, java.util.Collection, java.util.Collection, java.util.Collection)
	 */
	public void updateIA(IdentityAssoc ia, Collection<? extends IaAddress> addAddrs,
			Collection<? extends IaAddress> updateAddrs, Collection<? extends IaAddress> delAddrs)
	{
		if ((addAddrs != null) && !addAddrs.isEmpty()) {
			for (IaAddress addAddr : addAddrs) {
				DhcpLease lease = toDhcpLease(ia, addAddr);
				insertDhcpLease(lease);
			}
		}
		if ((updateAddrs != null) && !updateAddrs.isEmpty()) {
			for (IaAddress updateAddr : updateAddrs) {
				DhcpLease lease = toDhcpLease(ia, updateAddr);
				updateDhcpLease(lease);
			}
		}
		if ((delAddrs != null) && !delAddrs.isEmpty()) {
			for (IaAddress delAddr : delAddrs) {
				DhcpLease lease = toDhcpLease(ia, delAddr);
				deleteDhcpLease(lease);
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#deleteIA(com.jagornet.dhcpv6.db.IdentityAssoc)
	 */
	public void deleteIA(IdentityAssoc ia)
	{
		if (ia != null) {
			List<DhcpLease> leases = toDhcpLeases(ia);
			if ((leases != null) && !leases.isEmpty()) {
				for (final DhcpLease lease : leases) {
					deleteDhcpLease(lease);
				}
			}
		}
	}
	
	/**
	 * Insert dhcp lease.
	 *
	 * @param lease the lease
	 */
	protected void insertDhcpLease(final DhcpLease lease)
	{
		getJdbcTemplate().update("insert into dhcplease" +
				" (ipaddress, duid, iatype, iaid, prefixlen, state," +
				" starttime, preferredendtime, validendtime," +
				" ia_options, ipaddr_options)" +
				" values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
				new PreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps)
					throws SQLException {
				ps.setBytes(1, lease.getIpAddress().getAddress());
				ps.setBytes(2, lease.getDuid());
				ps.setByte(3, lease.getIatype());
				ps.setLong(4, lease.getIaid());
				ps.setShort(5, lease.getPrefixLength());
				ps.setByte(6, lease.getState());
				java.sql.Timestamp sts = 
					new java.sql.Timestamp(lease.getStartTime().getTime());
				ps.setTimestamp(7, sts, Util.GMT_CALENDAR);
				java.sql.Timestamp pts = 
					new java.sql.Timestamp(lease.getPreferredEndTime().getTime());
				ps.setTimestamp(8, pts, Util.GMT_CALENDAR);
				java.sql.Timestamp vts = 
					new java.sql.Timestamp(lease.getValidEndTime().getTime());
				ps.setTimestamp(9, vts, Util.GMT_CALENDAR);
				ps.setBytes(10, encodeOptions(lease.getIaDhcpOptions()));
				ps.setBytes(11, encodeOptions(lease.getIaAddrDhcpOptions()));
			}
		});
	}
	
	/**
	 * Update dhcp lease.
	 *
	 * @param lease the lease
	 */
	protected void updateDhcpLease(final DhcpLease lease)
	{
		getJdbcTemplate().update("update dhcplease" +
				" set state=?," +
				" starttime=?," +
				" preferredendtime=?," +
				" validendtime=?," +
				" ia_options=?," +
				" ipaddr_options=?" +
				" where ipaddress=?",
				new PreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps)
					throws SQLException {
				ps.setByte(1, lease.getState());
				java.sql.Timestamp sts = 
					new java.sql.Timestamp(lease.getStartTime().getTime());
				ps.setTimestamp(2, sts, Util.GMT_CALENDAR);
				java.sql.Timestamp pts = 
					new java.sql.Timestamp(lease.getPreferredEndTime().getTime());
				ps.setTimestamp(3, pts, Util.GMT_CALENDAR);
				java.sql.Timestamp vts = 
					new java.sql.Timestamp(lease.getValidEndTime().getTime());
				ps.setTimestamp(4, vts, Util.GMT_CALENDAR);
				ps.setBytes(5, encodeOptions(lease.getIaDhcpOptions()));
				ps.setBytes(6, encodeOptions(lease.getIaAddrDhcpOptions()));
				ps.setBytes(7, lease.getIpAddress().getAddress());
			}
		});
	}
	
	/**
	 * Delete dhcp lease.
	 *
	 * @param lease the lease
	 */
	protected void deleteDhcpLease(final DhcpLease lease)
	{
		getJdbcTemplate().update("delete from dhcplease" +
				" where ipaddress=?",
				new PreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps)
					throws SQLException {
				ps.setBytes(1, lease.getIpAddress().getAddress());
			}
		});
	}
	
	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#findDhcpOptionsByIdentityAssocId(long)
	 */
	@Override
	public List<DhcpOption> findDhcpOptionsByIdentityAssocId(long identityAssocId) {
		//TODO
		return null;
	}
	
	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#addDhcpOption(com.jagornet.dhcpv6.db.IdentityAssoc, com.jagornet.dhcpv6.db.DhcpOption)
	 */
	public void addDhcpOption(IdentityAssoc ia, DhcpOption option)
	{
		//TODO
	}
	
	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#updateDhcpOption(com.jagornet.dhcpv6.db.DhcpOption)
	 */
	public void updateDhcpOption(DhcpOption option)
	{
		//TODO
	}
	
	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#deleteDhcpOption(com.jagornet.dhcpv6.db.DhcpOption)
	 */
	public void deleteDhcpOption(DhcpOption option)
	{
		//TODO
	}
	
	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#getIA(long)
	 */
	public IdentityAssoc getIA(long id)
	{
		//TODO
		return null;
	}

	/**
	 * Find dhcp leases for ia.
	 *
	 * @param duid the duid
	 * @param iatype the iatype
	 * @param iaid the iaid
	 * @return the list
	 */
	protected List<DhcpLease> findDhcpLeasesForIA(final byte[] duid, final byte iatype, final long iaid)
	{
		return getJdbcTemplate().query(
                "select * from dhcplease" +
                " where duid = ?" +
                " and iatype = ?" +
                " and iaid = ?",
                new PreparedStatementSetter() {
            		@Override
            		public void setValues(PreparedStatement ps) throws SQLException {
            			ps.setBytes(1, duid);
            			ps.setByte(2, iatype);
            			ps.setLong(3, iaid);
            		}
            	},
                new DhcpLeaseRowMapper());
	}

	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#findIA(byte[], byte, long)
	 */
	public IdentityAssoc findIA(final byte[] duid, final byte iatype, final long iaid)
	{
        List<DhcpLease> leases = findDhcpLeasesForIA(duid, iatype, iaid);        
        return toIdentityAssoc(leases);
	}

	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#findIA(java.net.InetAddress)
	 */
	public IdentityAssoc findIA(final InetAddress inetAddr)
	{
		return findIA(inetAddr, true);
	}
	
	/**
	 * Find ia.
	 *
	 * @param inetAddr the inet addr
	 * @param allBindings the all bindings
	 * @return the identity assoc
	 */
	public IdentityAssoc findIA(final InetAddress inetAddr, boolean allBindings)
	{
        DhcpLease lease = getJdbcTemplate().query(
                "select * from dhcplease" +
                " where ipaddress = ?",
                new PreparedStatementSetter() {
            		@Override
            		public void setValues(PreparedStatement ps) throws SQLException {
            			ps.setBytes(1, inetAddr.getAddress());
            		}
            	},
                new DhcpLeaseResultSetExtractor());
        
        // use a set here, so that if we are getting all bindings, then we don't
        // include the lease found above again in the returned collection
        Set<DhcpLease> leases = new LinkedHashSet<DhcpLease>();
        leases.add(lease);
        if (allBindings) {
        	leases.addAll(findDhcpLeasesForIA(lease.getDuid(), lease.getIatype(), lease.getIaid()));
        }
        return toIdentityAssoc(leases);
	}
	
	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#updateIaAddr(com.jagornet.dhcpv6.db.IaAddress)
	 */
	public void updateIaAddr(final IaAddress iaAddr)
	{
		getJdbcTemplate().update("update dhcplease" +
				" set state = ?," +
				((iaAddr instanceof IaPrefix) ? " prefixlen = ?," : "") + 
				" starttime = ?," +
				" preferredendtime = ?," +
				" validendtime = ?" +
				" where ipaddress = ?",
				new PreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps) throws SQLException {
				int i = 1;
				ps.setByte(i++, iaAddr.getState());
				if (iaAddr instanceof IaPrefix) {
					ps.setShort(i++, ((IaPrefix)iaAddr).getPrefixLength());
				}
				Date start = iaAddr.getStartTime();
				if (start != null) {
					java.sql.Timestamp sts = new java.sql.Timestamp(start.getTime());
					ps.setTimestamp(i++, sts, Util.GMT_CALENDAR);
				}
				else {
					ps.setNull(i++, java.sql.Types.TIMESTAMP);
				}
				Date preferred = iaAddr.getPreferredEndTime();
				if (preferred != null) {
					java.sql.Timestamp pts = new java.sql.Timestamp(preferred.getTime());
					ps.setTimestamp(i++, pts, Util.GMT_CALENDAR);
				}
				else {
					ps.setNull(i++, java.sql.Types.TIMESTAMP);
				}
				Date valid = iaAddr.getValidEndTime();
				if (valid != null) {
					java.sql.Timestamp vts = new java.sql.Timestamp(valid.getTime());
					ps.setTimestamp(i++, vts, Util.GMT_CALENDAR);
				}
				else {
					ps.setNull(i++, java.sql.Types.TIMESTAMP);
				}
				ps.setBytes(i++, iaAddr.getIpAddress().getAddress());
			}
		});
	}
	
	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#deleteIaAddr(com.jagornet.dhcpv6.db.IaAddress)
	 */
	public void deleteIaAddr(final IaAddress iaAddr)
	{
		getJdbcTemplate().update("delete from dhcplease" +
				" where ipaddress = ?",
				new PreparedStatementSetter() {
			@Override
			public void setValues(PreparedStatement ps) throws SQLException {
				ps.setBytes(1, iaAddr.getIpAddress().getAddress());
			}
		});
	}
	
	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#updateIaPrefix(com.jagornet.dhcpv6.db.IaPrefix)
	 */
	public void updateIaPrefix(final IaPrefix iaPrefix)
	{
		updateIaAddr(iaPrefix);
	}
	
	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#deleteIaPrefix(com.jagornet.dhcpv6.db.IaPrefix)
	 */
	public void deleteIaPrefix(IaPrefix iaPrefix)
	{
		deleteIaAddr(iaPrefix);
	}

	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#findExistingIPs(java.net.InetAddress, java.net.InetAddress)
	 */
	@Override
	public List<InetAddress> findExistingIPs(final InetAddress startAddr, final InetAddress endAddr)
	{
        return getJdbcTemplate().query(
                "select ipaddress from dhcplease" +
                " where ipaddress >= ? and ipaddress <= ?" +
                " order by ipaddress", 
                new PreparedStatementSetter() {
					@Override
					public void setValues(PreparedStatement ps) throws SQLException {
						ps.setBytes(1, startAddr.getAddress());
						ps.setBytes(2, endAddr.getAddress());
					}                	
                },
                new RowMapper<InetAddress>() {
                    @Override
                    public InetAddress mapRow(ResultSet rs, int rowNum) throws SQLException {
                    	InetAddress inetAddr = null;
                    	try {
                			inetAddr = InetAddress.getByAddress(rs.getBytes("ipaddress"));
                		} 
                    	catch (UnknownHostException e) {
                    		// re-throw as SQLException
                			throw new SQLException("Unable to map ipaddress", e);
                		}
                        return inetAddr;
                    }
                });
	}
	
	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#findUnusedIaAddresses(java.net.InetAddress, java.net.InetAddress)
	 */
	@Override
	public List<IaAddress> findUnusedIaAddresses(final InetAddress startAddr, final InetAddress endAddr)
	{
		final long offerExpiration = new Date().getTime() - 12000;	// 2 min = 120 sec = 12000 ms
        return getJdbcTemplate().query(
                "select * from dhcplease" +
                " where ((state=" + IaAddress.ADVERTISED +
                " and starttime <= ?)" +
                " or (state=" + IaAddress.EXPIRED +
                " or state=" + IaAddress.RELEASED + "))" +
                " and ipaddress >= ? and ipaddress <= ?" +
                " order by state, validendtime, ipaddress",
                new PreparedStatementSetter() {
					@Override
					public void setValues(PreparedStatement ps) throws SQLException {
						java.sql.Timestamp ts = new java.sql.Timestamp(offerExpiration);
						ps.setTimestamp(1, ts);
						ps.setBytes(2, startAddr.getAddress());
						ps.setBytes(3, endAddr.getAddress());
					}                	
                },
                new IaAddrRowMapper());
	}
	
	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#findExpiredIaAddresses(byte)
	 */
	@Override
	public List<IaAddress> findExpiredIaAddresses(final byte iatype) {
        return getJdbcTemplate().query(
                "select * from dhcplease" +
                " where iatype = ?" +
                " and validendtime < ? order by validendtime",
                new PreparedStatementSetter() {
            		@Override
            		public void setValues(PreparedStatement ps) throws SQLException {
            			ps.setByte(1, iatype);
            			java.sql.Timestamp ts = new java.sql.Timestamp(new Date().getTime());
            			ps.setTimestamp(2, ts, Util.GMT_CALENDAR);
            		}
                },
                new IaAddrRowMapper());
	}
	
	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#findUnusedIaPrefixes(java.net.InetAddress, java.net.InetAddress)
	 */
	@Override
	public List<IaPrefix> findUnusedIaPrefixes(final InetAddress startAddr, final InetAddress endAddr) {
		final long offerExpiration = new Date().getTime() - 12000;	// 2 min = 120 sec = 12000 ms
        return getJdbcTemplate().query(
                "select * from dhcplease" +
                " where ((state=" + IaPrefix.ADVERTISED +
                " and starttime <= ?)" +
                " or (state=" + IaPrefix.EXPIRED +
                " or state=" + IaPrefix.RELEASED + "))" +
                " and ipaddress >= ? and ipaddress <= ?" +
                " order by state, validendtime, ipaddress",
                new PreparedStatementSetter() {
					@Override
					public void setValues(PreparedStatement ps) throws SQLException {
						java.sql.Timestamp ts = new java.sql.Timestamp(offerExpiration);
						ps.setTimestamp(1, ts);
						ps.setBytes(2, startAddr.getAddress());
						ps.setBytes(3, endAddr.getAddress());
					}                	
                },
                new IaPrefixRowMapper());
	}
	
	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#findExpiredIaPrefixes()
	 */
	@Override
	public List<IaPrefix> findExpiredIaPrefixes() {
        return getJdbcTemplate().query(
                "select * from dhcplease" +
                " where iatype = " + IdentityAssoc.PD_TYPE +
                " and validendtime < ? order by validendtime",
                new PreparedStatementSetter() {
            		@Override
            		public void setValues(PreparedStatement ps) throws SQLException {
            			java.sql.Timestamp ts = new java.sql.Timestamp(new Date().getTime());
            			ps.setTimestamp(1, ts, Util.GMT_CALENDAR);
            		}
                },
                new IaPrefixRowMapper());
	}
	
	/* (non-Javadoc)
	 * @see com.jagornet.dhcpv6.db.IaManager#reconcileIaAddresses(java.util.List)
	 */
	@Override
	public void reconcileIaAddresses(List<Range> ranges) {
		List<byte[]> args = new ArrayList<byte[]>();
		StringBuilder query = new StringBuilder();
		query.append("delete from dhcplease where ipaddress");
		Iterator<Range> rangeIter = ranges.iterator();
		while (rangeIter.hasNext()) {
			Range range = rangeIter.next();
			query.append(" not between ? and ?");
			args.add(range.getStartAddress().getAddress());
			args.add(range.getEndAddress().getAddress());
			if (rangeIter.hasNext())
				query.append(" and ipaddress");
		}
		getJdbcTemplate().update(query.toString(), args.toArray());
	}
	
	// Conversion methods
	
	/**
	 * To identity assoc.
	 *
	 * @param leases the leases
	 * @return the identity assoc
	 */
	public IdentityAssoc toIdentityAssoc(Collection<DhcpLease> leases)
	{
		IdentityAssoc ia = null;
		if ((leases != null) && !leases.isEmpty()) {
			Iterator<DhcpLease> leaseIter = leases.iterator();
			DhcpLease lease = leaseIter.next();
			ia = new IdentityAssoc();
			ia.setDuid(lease.getDuid());
			ia.setIatype(lease.getIatype());
			ia.setIaid(lease.getIaid());
			ia.setState(lease.getState());
			ia.setDhcpOptions(lease.getIaDhcpOptions());
			List<IaAddress> iaAddrs = new ArrayList<IaAddress>();
			iaAddrs.add(toIaAddress(lease));
			while (leaseIter.hasNext()) {
				//TODO: should confirm that the duid/iatype/iaid/state still match
				lease = leaseIter.next();
				iaAddrs.add(toIaAddress(lease));
			}
			ia.setIaAddresses(iaAddrs);
		}
		return ia;
	}

	/**
	 * To ia address.
	 *
	 * @param lease the lease
	 * @return the ia address
	 */
	public IaAddress toIaAddress(DhcpLease lease)
	{
		IaAddress iaAddr = new IaAddress();
		iaAddr.setIpAddress(lease.getIpAddress());
		iaAddr.setState(lease.getState());
		iaAddr.setStartTime(lease.getStartTime());
		iaAddr.setPreferredEndTime(lease.getPreferredEndTime());
		iaAddr.setValidEndTime(lease.getValidEndTime());
		iaAddr.setDhcpOptions(lease.getIaAddrDhcpOptions());
		return iaAddr;
	}
	
	/**
	 * To dhcp leases.
	 *
	 * @param ia the ia
	 * @return the list
	 */
	public List<DhcpLease> toDhcpLeases(IdentityAssoc ia)
	{
		if (ia != null) {
			Collection<? extends IaAddress> iaAddrs = ia.getIaAddresses();
			if ((iaAddrs != null) && !iaAddrs.isEmpty()) {
				List<DhcpLease> leases = new ArrayList<DhcpLease>();
				for (IaAddress iaAddr : iaAddrs) {
					DhcpLease lease = toDhcpLease(ia, iaAddr);
					leases.add(lease);
				}
				return leases;
			}
			else {
				log.warn("No addresses in lease");
			}
		}
		return null;
	}

	/**
	 * To dhcp lease.
	 *
	 * @param ia the ia
	 * @param iaAddr the ia addr
	 * @return the dhcp lease
	 */
	public DhcpLease toDhcpLease(IdentityAssoc ia, IaAddress iaAddr)
	{
		DhcpLease lease = new DhcpLease();
		lease.setDuid(ia.getDuid());
		lease.setIaid(ia.getIaid());
		lease.setIatype(ia.getIatype());
		lease.setIpAddress(iaAddr.getIpAddress());
		lease.setState(iaAddr.getState());
		lease.setStartTime(iaAddr.getStartTime());
		lease.setPreferredEndTime(iaAddr.getPreferredEndTime());
		lease.setValidEndTime(iaAddr.getValidEndTime());
		lease.setIaAddrDhcpOptions(iaAddr.getDhcpOptions());
		lease.setIaDhcpOptions(ia.getDhcpOptions());
		return lease;
	}
	
	/**
	 * Encode options.
	 *
	 * @param dhcpOptions the dhcp options
	 * @return the byte[]
	 */
	protected byte[] encodeOptions(Collection<DhcpOption> dhcpOptions)
	{
        if (dhcpOptions != null) {
        	ByteBuffer bb = ByteBuffer.allocate(1024);
            for (DhcpOption option : dhcpOptions) {
        		bb.putShort((short)option.getCode());
        		bb.putShort((short)option.getValue().length);
        		bb.put(option.getValue());
            }
            bb.flip();
            byte[] b = new byte[bb.limit()];
            bb.get(b);
            return b;
        }
        return null;
	}
	
	/**
	 * Decode options.
	 *
	 * @param buf the buf
	 * @return the collection
	 */
	protected Collection<DhcpOption> decodeOptions(byte[] buf)
	{
		Collection<DhcpOption> options = null;
        if (buf != null) {
        	ByteBuffer bb = ByteBuffer.wrap(buf);
        	options = new ArrayList<DhcpOption>();
        	while (bb.hasRemaining()) {
        		DhcpOption option = new DhcpOption();
        		option.setCode(bb.getShort());
        		int len = bb.getShort();
        		byte[] val = new byte[len];
        		bb.get(val);
        		option.setValue(val);
        		options.add(option);
        	}
        }
        return options;
	}

    /**
     * The Class DhcpLeaseRowMapper.
     */
    protected class DhcpLeaseRowMapper implements RowMapper<DhcpLease> 
    {    	
	    /* (non-Javadoc)
	     * @see org.springframework.jdbc.core.simple.RowMapper#mapRow(java.sql.ResultSet, int)
	     */
	    @Override
        public DhcpLease mapRow(ResultSet rs, int rowNum) throws SQLException {
	    	ResultSetExtractor<DhcpLease> rsExtractor = new DhcpLeaseResultSetExtractor();
	    	return rsExtractor.extractData(rs);
        }
    }
    
    /**
     * The Class DhcpLeaseResultSetExtractor.
     */
    protected class DhcpLeaseResultSetExtractor implements ResultSetExtractor<DhcpLease>
    {
		
		/* (non-Javadoc)
		 * @see org.springframework.jdbc.core.ResultSetExtractor#extractData(java.sql.ResultSet)
		 */
		@Override
		public DhcpLease extractData(ResultSet rs)
				throws SQLException, DataAccessException {
	    	DhcpLease lease = new DhcpLease();
	    	lease.setDuid(rs.getBytes("duid"));
	    	lease.setIatype(rs.getByte("iatype"));
	    	lease.setIaid(rs.getLong("iaid"));
        	try {
				lease.setIpAddress(InetAddress.getByAddress(rs.getBytes("ipaddress")));
			} 
        	catch (UnknownHostException e) {
        		// re-throw as SQLException
				throw new SQLException("Unable to map dhcplease", e);
			}
			lease.setStartTime(rs.getTimestamp("starttime", Util.GMT_CALENDAR));
			lease.setPreferredEndTime(rs.getTimestamp("preferredendtime", Util.GMT_CALENDAR));
			lease.setValidEndTime(rs.getTimestamp("validendtime", Util.GMT_CALENDAR));
			lease.setState(rs.getByte("state"));
			lease.setIaDhcpOptions(decodeOptions(rs.getBytes("ia_options")));
			lease.setIaAddrDhcpOptions(decodeOptions(rs.getBytes("ipaddr_options")));
            return lease;
		};
    }

    /**
     * The Class IaAddrRowMapper.
     */
    protected class IaAddrRowMapper implements RowMapper<IaAddress> 
    {
    	
	    /* (non-Javadoc)
	     * @see org.springframework.jdbc.core.simple.RowMapper#mapRow(java.sql.ResultSet, int)
	     */
	    @Override
        public IaAddress mapRow(ResultSet rs, int rowNum) throws SQLException {
        	IaAddress iaAddr = new IaAddress();
        	try {
				iaAddr.setIpAddress(InetAddress.getByAddress(rs.getBytes("ipaddress")));
			} 
        	catch (UnknownHostException e) {
        		// re-throw as SQLException
				throw new SQLException("Unable to map ipaddress", e);
			}
			iaAddr.setState(rs.getByte("state"));
			iaAddr.setStartTime(rs.getTimestamp("starttime", Util.GMT_CALENDAR));
			iaAddr.setPreferredEndTime(rs.getTimestamp("preferredendtime", Util.GMT_CALENDAR));
			iaAddr.setValidEndTime(rs.getTimestamp("validendtime", Util.GMT_CALENDAR));
            return iaAddr;
        }
    }

    /**
     * The Class IaPrefixRowMapper.
     */
    protected class IaPrefixRowMapper implements RowMapper<IaPrefix> 
    {
    	
	    /* (non-Javadoc)
	     * @see org.springframework.jdbc.core.simple.RowMapper#mapRow(java.sql.ResultSet, int)
	     */
	    @Override
        public IaPrefix mapRow(ResultSet rs, int rowNum) throws SQLException {
        	IaPrefix iaPrefix = new IaPrefix();
        	try {
				iaPrefix.setIpAddress(InetAddress.getByAddress(rs.getBytes("ipaddress")));
			} 
        	catch (UnknownHostException e) {
        		// re-throw as SQLException
				throw new SQLException("Unable to map ipaddress", e);
			}
        	iaPrefix.setPrefixLength(rs.getShort("prefixlen"));
			iaPrefix.setState(rs.getByte("state"));
			iaPrefix.setStartTime(rs.getTimestamp("starttime", Util.GMT_CALENDAR));
			iaPrefix.setPreferredEndTime(rs.getTimestamp("preferredendtime", Util.GMT_CALENDAR));
			iaPrefix.setValidEndTime(rs.getTimestamp("validendtime", Util.GMT_CALENDAR));
            return iaPrefix;
        }
    }
}