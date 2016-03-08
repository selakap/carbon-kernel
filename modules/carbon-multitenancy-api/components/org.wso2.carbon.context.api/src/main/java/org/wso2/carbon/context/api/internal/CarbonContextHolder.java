/*
 *  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.wso2.carbon.context.api.internal;

import org.wso2.carbon.context.api.CarbonContextUtils;
import org.wso2.carbon.context.api.TenantDomainSupplier;
import org.wso2.carbon.multitenancy.TenantRuntime;
import org.wso2.carbon.multitenancy.api.Tenant;
import org.wso2.carbon.multitenancy.exception.TenantException;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * This class will preserve an instance the current CarbonContext as a thread local variable. If a CarbonContext is
 * available on a thread-local-scope this class will do the required lookup and obtain the corresponding instance.
 *
 * @since 5.0.0
 */

public final class CarbonContextHolder {

    private Tenant tenant;
    private Principal userPrincipal;
    private Map<String, Object> properties = new HashMap<>();

    private static ThreadLocal<CarbonContextHolder> currentContextHolder = new ThreadLocal<CarbonContextHolder>() {
        protected CarbonContextHolder initialValue() {
            return new CarbonContextHolder();
        }
    };

    private CarbonContextHolder() {
        Optional<TenantRuntime> tenantRuntimeOptional = OSGiServiceHolder.getInstance().getTenantRuntime();
        Optional<String> systemTenantDomainOptional = tenantRuntimeOptional
                .flatMap(tenantRuntime -> CarbonContextUtils.getSystemTenantDomain());
        systemTenantDomainOptional.ifPresent(tenantDomain -> {
            try {
                tenant = tenantRuntimeOptional.get().loadTenant(tenantDomain);
            } catch (TenantException e) {
                throw new RuntimeException("Error occurred while trying to load tenant for " + tenantDomain, e);
            }
        });
    }

    /**
     * Method to obtain the current thread local CarbonContextHolder instance.
     *
     * @return the thread local CarbonContextHolder instance.
     */
    public static CarbonContextHolder getCurrentContextHolder() {
        return currentContextHolder.get();
    }

    /**
     * This method will destroy the current thread local CarbonContextHolder.
     */
    public void destroyCurrentCarbonContextHolder() {
        currentContextHolder.remove();
    }


    /**
     * Method to set a tenant on this CarbonContext instance. This method accepts a supplier which should provide the
     * tenant domain to load the tenant from tenant store.
     *
     * @param tenantDomainSupplier the supplier used to get the tenant domain.
     */
    public void setTenant(TenantDomainSupplier tenantDomainSupplier) {
        Optional<String> systemTenantDomainOptional = CarbonContextUtils.getSystemTenantDomain();
        Optional<TenantRuntime> tenantRuntimeOptional = OSGiServiceHolder.getInstance().getTenantRuntime();
        String tenantDomain = tenantDomainSupplier.get();
        //we don't set tenant if tenant domain is set at system level
        if (!systemTenantDomainOptional.isPresent()) {
            if (tenant == null) {
                tenant = tenantRuntimeOptional
                        .map(runtime -> {
                            try {
                                return runtime.loadTenant(tenantDomain);
                            } catch (TenantException e) {
                                throw new RuntimeException("Error occurred while trying to set tenant with domain : '" +
                                        tenantDomain + "'", e);
                            }
                        }).get();
            } else {
                Optional.of(tenant.getDomain())
                        .filter(currentTenantDomain -> currentTenantDomain.equals(tenantDomain))
                        .orElseThrow(() -> new IllegalStateException("Trying to override the current tenant " +
                                tenant.getDomain() + " to " + tenantDomain));
            }
        }
    }

    /**
     * Method to obtain the current tenant from the CarbonContext instance.
     *
     * @return the tenant instance.
     */
    public Tenant getTenant() {
        return tenant;
    }

    /**
     * Method to obtain a property on this CarbonContext instance.
     *
     * @param name the property name.
     * @return the value of the property by the given name.
     */
    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * Method to set a property on this CarbonContext instance.
     *
     * @param name  the property name.
     * @param value the value to be set to the property by the given name.
     */
    public void setProperty(String name, Object value) {
        properties.put(name, value);
    }

    /**
     * Method to obtain the currently set user principal from the CarbonContext instance.
     *
     * @return current user principal instance.
     */
    public Principal getUserPrincipal() {
        return userPrincipal;
    }

    /**
     * Checks and sets the given user principal to the current context.
     *
     * @param userPrincipal the user principal to be set
     */
    public void setUserPrincipal(Principal userPrincipal) {
        if (this.userPrincipal == null) {
            this.userPrincipal = userPrincipal;
        } else {
            Optional.ofNullable(this.userPrincipal.getName())
                    .filter(name -> userPrincipal.getName().equals(name))
                    .orElseThrow(() -> new IllegalStateException("Trying to override the already available user " +
                            "principal from " + this.userPrincipal.toString() + " to " +
                            userPrincipal.toString()));
        }
    }
}