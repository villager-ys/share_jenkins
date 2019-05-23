#! groopvy
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 默认tag
 * @return
 **/
@NonCPS
def getDefaultTag(){
    LocalDateTime localDateTime = LocalDateTime.now()
    localDateTime.format(DateTimeFormatter.ofPattern('yyyyMMddmmss',Locale.CHINESE))
}

/**
 * 流水线脚本
 * @return
 **/
def call(body){
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    assert  config.name: 'name不能为空'

    def needHost = config.needHost ?:'false'
    def imageName = params.IMAGE_NAME ?:config.name
    def deployENV = params.DEPLOY_ENV ?:'dev'
    def type = config.type ?:'maven'
    def coverage = config.coverage?:'10'

    def imageTag = params.IMAGE_TAG ?:getDefaultTag()
    def baseImage = params.BASE_IMAGE ?:env.BASE_IMAGE
    def cmd = params.CMD ?:''
    assert  baseImage:'基础镜像不能为空'

    pipeline{
        agent{
            kubernetes{
                label 'mypod'
                cloud 'kubernetes'
                yaml '''
apiVersion: v1
kind: Pod
metadata:
  labels:
    some-label: some-label-value
  namespace:hello
  name: mypod
spec:
  containers:
    -name: maven
     image: 1179325921/maven:1.1.1
     command:
     - cat 
     tty: true
     volumeMounts:
       - mountPath: /var/run/docker.sock
         name: docker-sock
       - mountPath: /usr/share/maven/repo
         name: tools-jenkins-maven
       - mountPath: /var/jenkins_home
         name: jenkins-data
       - mountPath: /root/.kube
         name: kubel
   volumes:
     - name: tools-jenkins-maven
       persistentVolumeClaim:
         claimName: tools-jenkins-maven-pc
     - hostPath:
         path: /data/jenkins
         type: ""
       name: jenkins-data
     - hostPath:
         path: /var/run/docker.sock
         type: ""
       name: docker-sock
     - hostPath:
         path: /root/.kube
         type: ""
       name: kubel
'''
            }
        }
        stages{
            stage('拉取代码'){
                steps{
                    echo 'fetch code from git'
                    checkout scm
                }
            }
            stage('maven parallel stage'){
                stage('单元测试'){
                    steps{
                        container('maven'){
                            echo 'starting unitTest...'
                            sh 'mvn -U -B org.jacoco-maven-plugin:prepare-agent clean verify -Dautoconfig.skip=ture -Dmaven.test.skip=false -Dmaven.test.failure.ingore=ture -f pom.xml'
                            junit allowEmptyResults:true,testResults:'**/targer/surefire-reports/*xml'
                            //单元测试覆盖率
                            jacoco changeBuildStatus:true,maximumLineCoverage:"${coverage}"
                            //测试报告
                            xuint([JUnit(deleteOutputFiles:true,failIfNotNew:true,pattern:'**/target/surefire-reports/*xml',skipNoTestFiles:false,stopProcessingIfError:true)])
                            withSonarQubeEnv('sonarqube'){
                                sh 'mvn -U sonar:sonar'
                            }
                        }
                    }
                }
                stage('maven构建'){
                    steps{
                        container('maven'){
                            sh 'mvn clean package -U -Dmaven.test.skip=true'
                        }
                    }
                }
            }
            stage('Quality Gate'){
                steps{
                    container('maven'){
                        scripts{
                            //获取soanrqube报告，设置超时1分钟
                            timeout(1){
                                //休息10秒，线程
                                sleep(10)
                                //利用sonar webhook功能通知pipeline代码检测结果，未通过质量阀，pipeline将会fail
                                def qg = waitForQualityGate()
                                if(qg.status != 'OK'){
                                    error("未通过SonarQube的代码质量阀检查，请及时修改！failure:${qg.status}")
                                }
                            }
                        }
                    }
                }
            }
            stage('docker build'){
                steps{
                    container('maven'){
                        sh label:'',script:'''#!/bin/bash
        echo -e 'FROM xxxx/libray/java-sw\nVOLUME /tmp\nARG JAR_FILE\nADD ${JAR_FILE} app.jar\nRUN touch /app.jar && ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime\nENTRYPOINT exec java $JAVA_OPTS -Duser.timezone=Asia/Shanghai -Djava.security.egd=file:/dev/./urandom -Dagent.service_name=${JOB_NAME} -Dcolllector.backend_service=skywalking-oap -jar /app.jar' > /home/jenkins/workspace/${JOB_NAME}/Dockerfile
        '''
                        sh "mvn -DskipTests -Ddocker.tag=latest dockerfile:build"
                        script{
                            sh 'docker login xxxxxx -uxx -pxx'
                            def image = docker.image(imageName)
                            image.push()
                            image.tag(imageTag)
                            image.push(imageTag)
                        }
                    }
                }
            }
            stage('deploy dev'){
                steps{
                    script{
                        if(needIngress == 'true'){
                            sh "sed -e 's#{APP_HOST}#${}#g;s#{APP_NAME}#s{name}#g' k8s-ingress.tpl > k8s-ingress.yml"
                            sh "kubectl apply -f k8s-ingress.yml --namespace=dev --kubeconfig='/root/.kube.config'"
                        }
                    }
                    sh "sed -e 's#{IMAGE_URL}#${imageName}#g;s#{APP_NAME}#${name};s#{SPRING_PROFILE}#dev#g;s#{JOS}#-Xmx128m -javaagent:/usr/skywalking/agent/skwwalking-agent.jar#g;s#{SVC_TYPE}#ClusterIP#g' k8s-deployment.tpl > k8s-deployment.ynl"
                    sh "kubectl apply -f k8s-deployment.yml --namespace=dev --kubecong='/root/.kube/config'"
                }
            }
            //无论是否出现错误都会执行
            post{
                always{
                    echo '删除资源'
                    cleanWs()
                    // 恢复测试数据库到初始状态

                }
            }
        }
    }


}