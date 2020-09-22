#!groovy

env.Version_ID = params.Version_ID
env.Func_Name = params.Func_Name
env.Package_Name = params.Package_Name
env.Chef_ENV = params.Chef_ENV

if ( params.Version_ID != null || params.Package_Name != null || params.Func_Name != null || params.Chef_ENV != null ) {

  properties([
    parameters([
      string(name: 'Version_ID', defaultValue: env.Version_ID, description: 'Please specify package version ID e.g: 17.1.0-SNAPSHOT'),
      choice(name: 'Chef_ENV', choices: env.Chef_ENV, description: 'Which environment should be deploy. It should be our chef environment e.g: use1nisuat'),
      choice(name: 'Func_Name', choices: "VehicleModelNotificationManagerFunction\nVehicleModelNotificationWorkerFunction\n", description: 'Which Lambda function should be deploy.'),
      string(name: 'Package_Name', defaultValue: env.Package_Name, description: 'The code package name, e.g: helios-lambda | helios-lambda-worker')
    ])
  ])

  node {
    // Wipe the workspace so we are building completely clean
    deleteDir()

    if ( env.Chef_ENV =~ /^use1/ ) {
      env.Aws_Region = 'us-east-1'
    } else if ( env.Chef_ENV =~ /^euw1/  ) {
      env.Aws_Region = 'eu-west-1'
    } else if ( env.Chef_ENV =~ /^apn1/  ) {
      env.Aws_Region = 'ap-northeast-1'
    }

    if ( env.Chef_ENV =~ /ren/ ) {
      env.Brand_Name = "Renault"
    } else if ( env.Chef_ENV =~ /nis/ ) {
      env.Brand_Name = "Nissan"
    }

    // Check if build exist in Nexus
/*    stage('Check and Get Code from Nexus') {
      wrap([$class: 'AnsiColorBuildWrapper']) {
        sh '''
          set +x
          if ! [[ `wget -S --spider "http://nexus.heliosalliance.net/service/local/artifact/maven/redirect?r=snapshots&g=com.digitaslbi.helios.standalone&a=${Package_Name}&v=${Version_ID}&p=jar" 2>&1 | grep 'HTTP/1.1 200 OK'` ]]; then
            echo "\u001B[31m --- The function code ${Version_ID} doesn't exist in Nexus --- \u001B[0m"
            exit 1
          fi
          CWD="$(pwd)"
          curl -L -o "${Package_Name}-${Version_ID}.jar"  "http://nexus.heliosalliance.net/service/local/artifact/maven/redirect?r=snapshots&g=com.digitaslbi.helios.standalone&a=${Package_Name}&v=${Version_ID}&p=jar"
        '''
        println "\u001B[32m --- The ${Package_Name}-${Version_ID}.jar has downloaded --- \u001B[0m"
      }
    }
*/

    stage('Update Function Code') {
      wrap([$class: 'AnsiColorBuildWrapper']) {
        withCredentials([[$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID',
        credentialsId: 'awsAccessKeyIdLambdaFunc' + env.Brand_Name, secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
          println "\u001B[32m --- Deploying ${Package_Name}-${Version_ID}.jar on Lambda function ${Chef_ENV}-${Func_Name} AWS region ${Aws_Region} --- \u001B[0m"
          sh '''
            set +x
            /usr/bin/aws --region ${Aws_Region} lambda update-function-code --function-name "${Chef_ENV}-${Func_Name}" --zip-file "fileb://${PWD}/${Package_Name}-${Version_ID}.jar"
          '''
        }
      }
    }

    step([$class: 'WsCleanup'])
    deleteDir()
  }
} else {
  // This project is parameterized
  stage('Create and Configure Pipeline') {
    def userInput = input(
      id: 'userInput', message: 'Please configure this job first!!', parameters: [
        [$class: 'TextParameterDefinition', name: 'VersionID', defaultValue: '', description: 'Please specify package version ID e.g: 17.1.0-SNAPSHOT'],
        [$class: 'TextParameterDefinition', name: 'PackageName', defaultValue: '', description: 'The code package name, e.g: helios-lambda | helios-lambda-worker'],
        [$class: 'ChoiceParameterDefinition', name: 'FuncName', choices: 'VehicleModelNotificationManagerFunction\nVehicleModelNotificationWorkerFunction\n', description: 'Which Lambda function should be deploy.'],
        [$class: 'TextParameterDefinition', name: 'ChefEnv', defaultValue: '', description: 'Environment, It should be our chef environment e.g: use1nisuat']
    ])

    properties([
      parameters([
        string(name: 'Version_ID', defaultValue: (userInput['VersionID']), description: 'Please specify package version ID e.g: 17.1.0-SNAPSHOT'),
        string(name: 'Package_Name', defaultValue: (userInput['PackageName']), description: 'The code package name, e.g: helios-lambda | helios-lambda-worker'),
        choice(name: 'Func_Name', choices: (userInput['FuncName']), description: 'Which Lambda function should be deploy.'),
        choice(name: 'Chef_ENV', choices: (userInput['ChefEnv']), description: 'Which environment should be deploy. It should be our chef environment e.g: use1nisuat')
      ])
    ])

    echo "Pipeline has created, please configure build parameters"
  }
}
