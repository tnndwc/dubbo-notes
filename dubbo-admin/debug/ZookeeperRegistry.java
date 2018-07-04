package com.alibaba.dubbo.registry.zookeeper;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ConcurrentHashSet;
import com.alibaba.dubbo.common.utils.UrlUtils;
import com.alibaba.dubbo.registry.NotifyListener;
import com.alibaba.dubbo.registry.support.FailbackRegistry;
import com.alibaba.dubbo.remoting.zookeeper.ChildListener;
import com.alibaba.dubbo.remoting.zookeeper.StateListener;
import com.alibaba.dubbo.remoting.zookeeper.ZookeeperClient;
import com.alibaba.dubbo.remoting.zookeeper.ZookeeperTransporter;
import com.alibaba.dubbo.rpc.RpcException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ZookeeperRegistry extends FailbackRegistry {
    private static final Logger logger = LoggerFactory.getLogger(ZookeeperRegistry.class);
    private static final int DEFAULT_ZOOKEEPER_PORT = 2181;
    private static final String DEFAULT_ROOT = "dubbo";
    private final String root;
    private final Set<String> anyServices = new ConcurrentHashSet();
    private final ConcurrentMap<URL, ConcurrentMap<NotifyListener, ChildListener>> zkListeners = new ConcurrentHashMap();
    private final ZookeeperClient zkClient;

    public ZookeeperRegistry(URL url, ZookeeperTransporter zookeeperTransporter) {
        super(url);
        if (url.isAnyHost()) {
            throw new IllegalStateException("registry address == null");
        } else {
            String group = url.getParameter("group", "dubbo");
            if (!group.startsWith("/")) {
                group = "/" + group;
            }

            this.root = group;
            this.zkClient = zookeeperTransporter.connect(url);
            this.zkClient.addStateListener(new StateListener() {
                public void stateChanged(int state) {
                    if (state == 2) {
                        try {
                            ZookeeperRegistry.this.recover();
                        } catch (Exception var3) {
                            ZookeeperRegistry.logger.error(var3.getMessage(), var3);
                        }
                    }

                }
            });
        }
    }

    static String appendDefaultPort(String address) {
        if (address != null && address.length() > 0) {
            int i = address.indexOf(58);
            if (i < 0) {
                return address + ":" + 2181;
            }

            if (Integer.parseInt(address.substring(i + 1)) == 0) {
                return address.substring(0, i + 1) + 2181;
            }
        }

        return address;
    }

    public boolean isAvailable() {
        return this.zkClient.isConnected();
    }

    public void destroy() {
        super.destroy();

        try {
            this.zkClient.close();
        } catch (Exception var2) {
            logger.warn("Failed to close zookeeper client " + this.getUrl() + ", cause: " + var2.getMessage(), var2);
        }

    }

    protected void doRegister(URL url) {
        try {
            this.zkClient.create(this.toUrlPath(url), url.getParameter("dynamic", true));
        } catch (Throwable var3) {
            throw new RpcException("Failed to register " + url + " to zookeeper " + this.getUrl() + ", cause: " + var3.getMessage(), var3);
        }
    }

    protected void doUnregister(URL url) {
        try {
            this.zkClient.delete(this.toUrlPath(url));
        } catch (Throwable var3) {
            throw new RpcException("Failed to unregister " + url + " to zookeeper " + this.getUrl() + ", cause: " + var3.getMessage(), var3);
        }
    }

    protected void doSubscribe(final URL url, final NotifyListener listener) {
        try {
            if ("*".equals(url.getServiceInterface())) {
                String root = this.toRootPath();
                ConcurrentMap<NotifyListener, ChildListener> listeners = (ConcurrentMap) this.zkListeners.get(url);
                if (listeners == null) {
                    this.zkListeners.putIfAbsent(url, new ConcurrentHashMap());
                    listeners = (ConcurrentMap) this.zkListeners.get(url);
                }

                ChildListener zkListener = (ChildListener) listeners.get(listener);
                if (zkListener == null) {
                    listeners.putIfAbsent(listener, new ChildListener() {
                        public void childChanged(String parentPath, List<String> currentChilds) {
                            Iterator i$ = currentChilds.iterator();

                            while (i$.hasNext()) {
                                String child = (String) i$.next();
                                child = URL.decode(child);
                                if (!ZookeeperRegistry.this.anyServices.contains(child)) {
                                    ZookeeperRegistry.this.anyServices.add(child);
                                    ZookeeperRegistry.this.subscribe(url.setPath(child).addParameters(new String[]{"interface", child, "check", String.valueOf(false)}), listener);
                                }
                            }

                        }
                    });
                    zkListener = (ChildListener) listeners.get(listener);
                }

                this.zkClient.create(root, false);
                List<String> services = this.zkClient.addChildListener(root, zkListener);
                if(services != null && services.size() > 0){
                    List<String> list = new ArrayList<>();
                    for (String service : services) {
                        if(service.contains(只拉取包含特定字符的 Service)){
                            list.add(service);
                        }
                    }
                    services = list;
                }
                if (services != null && !services.isEmpty()) {
                    Iterator i$ = services.iterator();

                    while (i$.hasNext()) {
                        String service = (String) i$.next();
                        service = URL.decode(service);
                        this.anyServices.add(service);
                        this.subscribe(url.setPath(service).addParameters(new String[]{"interface", service, "check", String.valueOf(false)}), listener);
                    }
                }
            } else {
                List<URL> urls = new ArrayList();
                String[] arr$ = this.toCategoriesPath(url);
                int len$ = arr$.length;

                for (int i$ = 0; i$ < len$; ++i$) {
                    String path = arr$[i$];
                    ConcurrentMap<NotifyListener, ChildListener> listeners = (ConcurrentMap) this.zkListeners.get(url);
                    if (listeners == null) {
                        this.zkListeners.putIfAbsent(url, new ConcurrentHashMap());
                        listeners = (ConcurrentMap) this.zkListeners.get(url);
                    }

                    ChildListener zkListener = (ChildListener) listeners.get(listener);
                    if (zkListener == null) {
                        listeners.putIfAbsent(listener, new ChildListener() {
                            public void childChanged(String parentPath, List<String> currentChilds) {
                                ZookeeperRegistry.this.notify(url, listener, ZookeeperRegistry.this.toUrlsWithEmpty(url, parentPath, currentChilds));
                            }
                        });
                        zkListener = (ChildListener) listeners.get(listener);
                    }

                    this.zkClient.create(path, false);
                    List<String> children = this.zkClient.addChildListener(path, zkListener);
                    if (children != null) {
                        urls.addAll(this.toUrlsWithEmpty(url, path, children));
                    }
                }

                this.notify(url, listener, urls);
            }

        } catch (Throwable var11) {
            throw new RpcException("Failed to subscribe " + url + " to zookeeper " + this.getUrl() + ", cause: " + var11.getMessage(), var11);
        }
    }

    protected void doUnsubscribe(URL url, NotifyListener listener) {
        ConcurrentMap<NotifyListener, ChildListener> listeners = (ConcurrentMap) this.zkListeners.get(url);
        if (listeners != null) {
            ChildListener zkListener = (ChildListener) listeners.get(listener);
            if (zkListener != null) {
                this.zkClient.removeChildListener(this.toUrlPath(url), zkListener);
            }
        }

    }

    public List<URL> lookup(URL url) {
        if (url == null) {
            throw new IllegalArgumentException("lookup url == null");
        } else {
            try {
                List<String> providers = new ArrayList();
                String[] arr$ = this.toCategoriesPath(url);
                int len$ = arr$.length;

                for (int i$ = 0; i$ < len$; ++i$) {
                    String path = arr$[i$];
                    List<String> children = this.zkClient.getChildren(path);
                    if (children != null) {
                        providers.addAll(children);
                    }
                }

                return this.toUrlsWithoutEmpty(url, providers);
            } catch (Throwable var8) {
                throw new RpcException("Failed to lookup " + url + " from zookeeper " + this.getUrl() + ", cause: " + var8.getMessage(), var8);
            }
        }
    }

    private String toRootDir() {
        return this.root.equals("/") ? this.root : this.root + "/";
    }

    private String toRootPath() {
        return this.root;
    }

    private String toServicePath(URL url) {
        String name = url.getServiceInterface();
        return "*".equals(name) ? this.toRootPath() : this.toRootDir() + URL.encode(name);
    }

    private String[] toCategoriesPath(URL url) {
        String[] categories;
        if ("*".equals(url.getParameter("category"))) {
            categories = new String[]{"providers", "consumers", "routers", "configurators"};
        } else {
            categories = url.getParameter("category", new String[]{"providers"});
        }

        String[] paths = new String[categories.length];

        for (int i = 0; i < categories.length; ++i) {
            paths[i] = this.toServicePath(url) + "/" + categories[i];
        }

        return paths;
    }

    private String toCategoryPath(URL url) {
        return this.toServicePath(url) + "/" + url.getParameter("category", "providers");
    }

    private String toUrlPath(URL url) {
        return this.toCategoryPath(url) + "/" + URL.encode(url.toFullString());
    }

    private List<URL> toUrlsWithoutEmpty(URL consumer, List<String> providers) {
        List<URL> urls = new ArrayList();
        if (providers != null && !providers.isEmpty()) {
            Iterator i$ = providers.iterator();

            while (i$.hasNext()) {
                String provider = (String) i$.next();
                provider = URL.decode(provider);
                if (provider.contains("://")) {
                    URL url = URL.valueOf(provider);
                    if (UrlUtils.isMatch(consumer, url)) {
                        urls.add(url);
                    }
                }
            }
        }

        return urls;
    }

    private List<URL> toUrlsWithEmpty(URL consumer, String path, List<String> providers) {
        List<URL> urls = this.toUrlsWithoutEmpty(consumer, providers);
        if (urls == null || urls.isEmpty()) {
            int i = path.lastIndexOf(47);
            String category = i < 0 ? path : path.substring(i + 1);
            URL empty = consumer.setProtocol("empty").addParameter("category", category);
            urls.add(empty);
        }

        return urls;
    }
}