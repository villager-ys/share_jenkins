#! groovy

def call(imageName,imageTag,coverage,cmd) {
    echo '基础镜像：${baseImage},镜像名：${imageName},镜像标签：${imageTag}'
    def baseImage = env.BASE_IMAGE
    //单元测试
    stage('单元测试'){
        echo "starting unitTest....."
        sh "mvn -U -B org.jacoco:jacoco-maven-plugin:prepare-agent clean verify -Dautoconfig.skip=true -Dmaven.test.skip=false -Dmaven.test.failure.ignore=true -f pom.xml"
        junit allowEmptyResults: true,testResults:'**/target/surefire-reports/*xml'

        //单元测试覆盖率
        jacoco changeBuildStatus:true,maximumLineCoverage: "${coverage}"
        //测试报告
        xunit([JUnit(deleteOutputFiles:ture,failIfNotNew:true,pattern:'**/target/surefire-reports/*xml',skipNoTestFiles:false,stopProcessingIfError:true)])
    }

    //sonar分析
    stage('静态代码检查'){
        echo "starting sonar analyze......"
        withSonarQubeEnv('sonarque'){
            sh 'mvn -U sonar:sonar'
        }
    }

    stage('Quality Gate'){
        timeout(time: 1,uint: 'HOURS'){
            waitForQualitGate abortPipeline: true
        }
    }

    // maven构建
    stage('maven构建'){
        if(cmd){
            sh "$cmd"
        }
        sh "mvn clean package -U -Dmavne.test.skip=true"
        //构建完docker打包，push
        dockerBuild(baseImage,imageName,imageTag)
    }
}