#! groovy

def call(baseImage,imageName,imageTag){
    echo 'dcoker打包：基础镜像：${baseImage},镜像名：${imageName},镜像标签：${imageTag}'
    stage('镜像构建'){
        withEnv(["BASE_IMAGE=${baseImage}","Image_Tag=${imageTag}"]){
            sh label:'',script:'''#!/bin/bash
        echo -e 'FROM xxxx/libray/java-sw\nVOLUME /tmp\nARG JAR_FILE\nADD ${JAR_FILE} app.jar\nRUN touch /app.jar && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime\nENTRYPOINT exec java $JAVA_OPTS -Duser.timezone=Asia/Shanghai -Djava.security.egd=file:/dev/./urandom -Dagent.service_name=${JOB_NAME} -Dcolllector.backend_service=skywalking-oap -jar /app.jar' > /home/jenkins/workspace/${JOB_NAME}/Dockerfile
        '''
            sh "mvn -DskipTests -Ddocker.tag=latest dockerfile:build"
        }
    }
    stage('镜像推送'){
        def image = docker.image(imageName)
        image.tag(imageTag)
        sh 'docker login xxxxxx -uxx -pxx'
        image.push(imageTag)
    }
}
