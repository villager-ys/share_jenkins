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

    def label = "mypod-${UUID.randomUUID().toString()}"
    podTemplate(label:label,cloud:'kubernetes',namespace:'tools',container:[
            containerTemplate(
                    name:'test',
                    image:'1179325921/jenkins-slave:1.0.5',
                    ttyEnabled:true,
                    command:'cat'
            )
    ],
            volumes:[
                    hostPathVolume(mountPath:'/usr/bin/docker',hostPath:'/usr/bin/docker'),
                    hostPathVolume(mountPath:'/usr/bin/docker.sock',hostPath:'/var/run/docker.sock'),
                    hostPathVolume(mountPath:'/root/.kube',hostPath:'/root/.kube'),
                    hostPathVolume(claimName:'tools-jenkins-maven-pvc',mountPath:'/usr/local/apache-maven-3.3.9/repo',readOnly:false)
            ]
    ){
        node(label){
            cotianer('test'){
                if(params.BUILD_MODE != 'DEPLOY_ONLY'){
                    stage('代码检出'){
                        echo '拉去代码'
                        checkout scm
                    }
                }
                if(params.BUILD_MODE != 'DEPLOY_ONLY'){
                    switch(type){
                        case 'maven':
                            withEnv(["BASE_IMAGE=${baseImage}"]){
                                mavenProject(imageName,imageTag,coverage,cmd)
                            }
                            break;
                        default:
                            error '目前仅支持maven项目构建，构建失败'
                            return
                    }
                }
                if(params.BUILD_MODE!='BUILD_ONLY'&&params.DEPLOY_TO == 'kubernetes'){
                    if(params.BUILD_MODE=='DEPLOY_ONLY'){
                        assert params.IMAGE_TAG:'IMAGE_TAG不能为空'
                    }
                    timestamps{
                        withEnv(["PATH=/usr/local/bin:${PATH}",DEPLOY_ENV=${deployENV}]){
                            k8sDeploy(config.name,"${imageName}:${imageTag}",needHost)
                        }
                    }
                }
                stage('Completed'){
                    echo '已完成，正在删除资源'
                    cleanWs()
                }
            }
        }
    }
}