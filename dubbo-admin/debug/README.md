### 每次启动从 zookeeper 拉取“有限”的数据

对于拥有成千上万个 dubbo 服务的环境来说（测试环境），每次启动 admin 都非常慢，因为它需要从 dubbo registry 拉取全量的服务相关数据，所以，为了加快 admin 启动速度，方便对其进行学习和 debug，我采用如下方法来过滤要拉取数据：

#### 思路
 - 修改 com.alibaba.dubbo.registry.zookeeper.ZookeeperRegistry.java 源代码，在代码中设定过滤条件
 - 使用 javaagent 热替换 ZookeeperRegistry.java 的实现

#### 步骤
 - 找一份 com.alibaba.dubbo.registry.zookeeper.ZookeeperRegistry 源码，重写 doSubscribe 方法部分逻辑，例如：根据 services 名称作为过滤条件。
 - 将 javaagent_debug 打包成 debugagent.jar：jar cfm debugagent.jar javaagent_debug/MANIFEST.MF javaagent_debug/*.class
 - 将 debugagent.jar 加入到 admin 所在到 classpath 中
 - 在 admin 的启动参数中加入：-javaagent:目录/debugagent.jar

至此，就可以在 IDE 里很愉快的对 admin 进行 debug 和测试了。