# Hashi-Tools

A Scala-based SDK for Hashicorp Vault and Consul.

## Secrets

The SDK Scala object of Vault is called 'Secrets'.

It is intended to be used by Scala and Java Applications.

'Secrets' provides a friendly DSL API.

![image](/pics/running_example.png?raw=true "example")

### Features

* Secrets help authenticate to Vault with an authentication method
* Provides an out-of-the-box implementation for AWS AMI method
* Renews its own Vault token automatically
* Creates secrets for Secret Engines
* Renews the secrets automatically

### Installing the SDK

SBT:

````
libraryDependencies += "com.fyber" % "hashi-tools_2.12" % "x.x.x"
````

Maven:

````
<dependency>
    <groupId>com.fyber</groupId>
    <artifactId>hashi-tools_2.12</artifactId>
    <version>x.x.x</version>
</dependency>

````

### Configuration
Vaults can be configured in multiple ways. The recommended method is to configure 'Secrets' in the native Scala configuration file (i.e. application.conf).

#### General Configuration

Below is an example of the general configuration:

````
  secrets {
    host = ""
    port = (8200)
    protocol = "HTTP"
    readTimeout = (Int)
    auth = {
      vaultRole = ""
      vaultPolicy = ""
      method = "" 
      properties {
        role = "" 
        awsAccountId = ""
        vaultHeader = "" 
      }
      //method = "", 
      //properties {
      //  token = ""
      //}
      renew {
        scheduled = true, 
        scheduleDuration = "" 
        increment = (Int, Seconds)
      }
    },
    engines { // supported secret engines (multiple)
      aws { //AWS secret Engine
        mount = "" // default is "aws"
        keypath = // path for the role, required for creating the service credentials
        retry{ // retry in case of failure while accessing vault server
          retries{
            maxRetries =
            retryIntervalMilliseconds = (ms)
          }
        }
        renew{
          scheduled = true, //automatic token renew
          scheduleDuration = (Duration)
          increment = (Int, Seconds)
        }
      }
    }
    database {
       mount = "" //default is "database"
       keypath =
       retry {
           retries {
           maxRetries = 3
             retryIntervalMilliseconds = (ms)
           }
         }
       renew {
          scheduled = true, //automatic token renew
          scheduleDuration = (Duration)
          increment = (Int, Seconds)
         }
       }
     }
  }
````

#### Global Properties

These properties define the interface against the Vault-Server.

````
secrets {
  host = //Vault server host url. If not specified, will be taken from a local Consul agent.
  port = (8200),
  protocol = "http" //if missing has a default fallback to http
  readTimeout =  //read credentials timeout in seconds
}
````


#### Authentication Methods

Auth methods are the components in Vault that perform authentication.  They are responsible for assigning identity and a set of policies to a user.
An authentication method creates a temporary Vault-token for the application to identify against the Vault-Server.

The configuration for Authentication method are as follows:

````
auth = { // required => specify one Authentication method from (AWSIam, VaultToken)
  vaultRole = "role-name"
  vaultPolicy = "policy-name"
  method = "Auth Method name"
  properties { // Auth method unique properties
      }
  renew {
    scheduled = true, // Vault Auth token automatic renew
    scheduleDuration = "2 hours" // relevant when "scheduled = true", the interval between the token renwal executions
    increment = 43200 // The token expiration time would be set to the "increment" vaule
    }
````

Note:  You must define an authentication method for 'Secrets'.  Their authentication with a Vault token is also considered as an Authentication method.
Also note, if you configure 'VaultToken' as the authentication method, you won't want the client to automatically increment its expiration time.  Otherwise, you must set the 'scheduled' parameter to true.

##### Token

Requires a pre-generated Vault token with an appropriate policy for 'Secrets' to use.

The configuration for Token authentication method looks like this:

````
    auth = {
      ..
      method = "VaultToken" // use AWS-IAM authentication
      properties {
        token = "vault-token" // AWS role to assume
      }
    ..

````

##### AWS IAM

Requires pre-configured [IAM method](https://www.vaultproject.io/docs/auth/aws.html) in the Vault-server cluster.

The role of the AWS instance must have the following permissions (Policy):

````
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ec2:DescribeInstances",
        "iam:GetInstanceProfile",
        "iam:GetUser",
        "iam:GetRole"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": ["sts:AssumeRole"],
      "Resource": [
        "arn:aws:iam::<AccountId>:role/<VaultRole>"
      ]
    }
  ]
}
````

The vault configuration for AWS IAM looks like this:

````
  secrets {
    auth = {
      method = "AWSIam" // use AWS-IAM authentication
      properties {
        role = "aws-iam-role" // AWS role to assume
        awsAccountId = "aws-account-id" // AWS account ID
        vaultHeader = "header" // X-Vault-AWS-IAM-Server-ID value
      }
    }
````


#### Secrets Engines

Requires pre-configured [Secret engines](https://docs.aws.amazon.com/IAM/latest/UserGuide/troubleshoot_general.html#troubleshoot_general_eventual-consistency) in the Vault-server cluster.

Multiple engines can be configured.

The 'AWS Secrets Engine' key is called 'aws'.  The Database is called 'database'

*Note: [It usually takes a few seconds for a change in AWS IAM to be updated in the AWS servers](https://www.vaultproject.io/docs/secrets/)*
![image](/pics/IAM_changes_take_time.png?raw=true "example")
*Use the 'getCredentialsAndWait'* if you want to use the keys immediately after accepting them from the client.(See example below)

*Also note, the Vault KV-engine does not require any configuration.*


Configure them as follows:

````
    engines { // supported secret engines (multiple)
      aws { //AWS secret Engine
        keypath = // path for the role, required for creating the service credentials
        retry{ // retry in case of failure while accessing vault server
          retries{
            maxRetries = 3
            retryIntervalMilliseconds = (ms)
          }
        }
        renew{
          scheduled = true, //automatic token renew
          scheduleDuration = (Duration)
          increment = (Seconds)
        }
      }
    }
    database {
       keypath =
       retry {
           retries {
           maxRetries = 3
             retryIntervalMilliseconds = 1000
           }
         }
       renew {
          scheduled = true, //automatic token renew
          scheduleDuration = (Duration)
          increment = (Seconds)
         }
       }
     }
  }
````

### Usage Examples

````scala

import com.typesafe.config.ConfigFactory
import com.fyber.vault.client.Secrets
import com.fyber.vault.engines.AWS.AWSCredentials
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

def config = ConfigFactory.load().getConfig("secrets")

val secrets = Secrets(config)

println("--------------------------------------------------------")
println("Get AWS Credentials")

val awscredentials = secrets.engines.aws.getCredentials
val awsAccessKey = awscredentials.accessKey.key
val awsSecretKey = awscredentials.secretKey.key

val awscredentialsVerifyedAsReady = secrets.engines.aws.getCredentialsAndWait

println("--------------------------------------------------------")
println("Get AWS Credentials in async way")

val credentialsAsync: Future[AWSCredentials] = secrets.engines.aws.getCredentialsAsync

val result = Await.result(credentialsAsync, Duration.Inf)

println(s" accessKey: ${result.accessKey.key}")

println("--------------------------------------------------------")
println("Get AWS Credentials for different mount")

val credentialsProduction = secrets.engines.aws.getCredentials("prod")
val awsAccessKeyProduction = credentialsProduction.accessKey.key

println("--------------------------------------------------------")
println("Get database Credentials")

val databaseCredentials = secrets.engines.database.getCredentials
val userName = databaseCredentials.user.userName
val password = databaseCredentials.password.password

println("--------------------------------------------------------")
val myValue = secrets.engines.kv.getCredentials(Path("my-secret"), Key("my-value"))

````

#### Monitoring

Hashi-tools uses [Kamon](https://kamon.io/) as the instrumenting monitoring tool.
To allow monitoring, set the 'monitoring' property to 'true'
{
  secrets {
    monitoring = true
    ...
  }
}

Hashi-tools uses its own Kamon object.  If not, such objects are passed in the 'Secrets' object.
The configuration for Kamon is set in the 'kamon.conf' file.
By default, Kamon uses its own dispatcher, to port 8127.

````
kamon: {

  metric: {
    tick-interval: "10 seconds"
  },
  prometheus {
    include-environment-tags: "no",
    embedded-server {
      port = 8127
    }
  },
  internal-config: {
    akka: {
      actor: {
        default-dispatcher: {
          fork-join-executor: {
            parallelism-factor: 0.5,
            parallelism-max: 2,
            parallelism-min: 1
          }
        }
      }
    }
  }
}
````

The 'Secrets' object can get an existing Kamon-Vault object.
To to this, the object must extend 'VaultMonitorEngine', and override the 'historgram' method, i.e.:

````scala
private class VaultMonitorAdapter() extends VaultMonitorEngine {

  override def histogram(name: String, tags: (String, String)*): Unit = myMonitor.histogram(name, 1, tags: _*)

}
````
and then:
````scala

val secrets = Secrets(config, Some(new VaultMonitorAdapter()))

````

### Dependencies

'Secrets' uses [BetterCloud's Java-Driver](https://github.com/BetterCloud/vault-java-driver) for The HTTP-API against the vault server.

## Discovery

The SDK Scala object of Consul called 'Discovery'.

It is intended to be used by Scala and Java Applications.

### Usage Examples

````scala
val discovery = Discovery()
val healthyServers: List[String] = discovery.getHealthServices("Mongodb")
````
