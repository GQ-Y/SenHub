# 使用ARM64 Ubuntu镜像
FROM ubuntu:22.04

# 设置环境变量
ENV DEBIAN_FRONTEND=noninteractive

# 安装基础工具和依赖
RUN apt-get update && apt-get install -y \
    openjdk-11-jdk \
    wget \
    curl \
    git \
    build-essential \
    file \
    && rm -rf /var/lib/apt/lists/*

# 设置Java环境变量（Ubuntu 22.04 ARM64的Java路径）
RUN update-alternatives --install /usr/bin/java java /usr/lib/jvm/java-11-openjdk-arm64/bin/java 1 && \
    update-alternatives --install /usr/bin/javac javac /usr/lib/jvm/java-11-openjdk-arm64/bin/javac 1

ENV JAVA_HOME=/usr/lib/jvm/java-11-openjdk-arm64
ENV PATH=$JAVA_HOME/bin:$PATH

# 创建工作目录
WORKDIR /app

# 复制项目文件
COPY . /app/

# 设置SDK库文件路径（通过挂载）
ENV LD_LIBRARY_PATH=/app/lib:/app/../sdk/HCNetSDKV6.1.9.45_build20220902_ArmLinux64_ZH/MakeAll:/app/../sdk/HCNetSDKV6.1.9.45_build20220902_ArmLinux64_ZH/MakeAll/HCNetSDKCom

# 安装Maven
RUN wget https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.tar.gz && \
    tar -xzf apache-maven-3.9.6-bin.tar.gz -C /opt && \
    mv /opt/apache-maven-3.9.6 /opt/maven && \
    rm apache-maven-3.9.6-bin.tar.gz

# 设置Maven环境变量（必须在验证之前）
ENV MAVEN_HOME=/opt/maven
ENV PATH=$MAVEN_HOME/bin:$PATH

# 验证安装
RUN java -version && /opt/maven/bin/mvn -version

# 默认命令
CMD ["/bin/bash"]
