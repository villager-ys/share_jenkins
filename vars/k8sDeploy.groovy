#! groovy

def call(name,imageName,needIngress){
    def envMap = ['dev':'xxxxxx','test':'xxxx','pro':'xxxx']
    def apiServer = envMap[DEPLOY_ENV]

    assert  name: 'name不能为空'
    assert  imageName : 'imageName不能为空'

    stage('Deploy'){
        if (DEPLOY_ENV == 'dev'){
            if(needIngress == 'true'){
                sh "sed -e 's#{APP_HOST}#${}#g;s#{APP_NAME}#s{name}#g' k8s-ingress.tpl > k8s-ingress.yml"
                sh "kubectl apply -f k8s-ingress.yml --namespace=dev --kubeconfig='/root/.kube.config'"
            }
            sh "sed -e 's#{IMAGE_URL}#${imageName}#g;s#{APP_NAME}#${name};s#{SPRING_PROFILE}#dev#g;s#{JOS}#-Xmx128m -javaagent:/usr/skywalking/agent/skwwalking-agent.jar#g;s#{SVC_TYPE}#ClusterIP#g' k8s-deployment.tpl > k8s-deployment.ynl"
            sh "kubectl apply -f k8s-deployment.yml --namespace=dev --kubecong='/root/.kube/config'"
        }
    }
}
