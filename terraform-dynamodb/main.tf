# -------------------------------
# 1. DynamoDB Users Table
# -------------------------------
resource "aws_dynamodb_table" "users" {
  name           = "users"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "PK"

  attribute {
    name = "PK"
    type = "S"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = false
  }

  tags = {
    Environment = "dev"
    Project     = "BusinessExpenseTracker"
  }
}

# -------------------------------
# 2. IAM Role for Lambda Execution
# -------------------------------
resource "aws_iam_role" "lambda_exec_role" {
  name = "lambda-exec-role-otp"

  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Action = "sts:AssumeRole",
        Effect = "Allow",
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

# Attach policies to role
resource "aws_iam_role_policy_attachment" "lambda_basic_execution" {
  role       = aws_iam_role.lambda_exec_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy_attachment" "dynamodb_access" {
  role       = aws_iam_role.lambda_exec_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonDynamoDBFullAccess"
}

resource "aws_iam_role_policy_attachment" "ses_access" {
  role       = aws_iam_role.lambda_exec_role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSESFullAccess"
}

# -------------------------------
# 3. Lambda Function - triggerOtpLambda
# -------------------------------
resource "aws_lambda_function" "trigger_otp_lambda" {
  function_name = "triggerOtpLambda"
  role          = aws_iam_role.lambda_exec_role.arn
  handler       = "com.bet.TriggerOtpLambda::handleRequest"
  runtime       = "java17"
  timeout       = 15
  memory_size   = 512

  filename         = "${path.module}/../lambda/target/trigger-otp-lambda-1.0.0.jar"
  source_code_hash = filebase64sha256("${path.module}/../lambda/target/trigger-otp-lambda-1.0.0.jar")

  environment {
    variables = {
      SES_FROM_ADDRESS = "no-reply@yourdomain.com"
    }
  }
}

# -------------------------------
# 4. API Gateway Setup
# -------------------------------
resource "aws_apigatewayv2_api" "lambda_http_api" {
  name          = "triggerOtpApi"
  protocol_type = "HTTP"
}

resource "aws_lambda_permission" "api_gateway_invoke" {
  statement_id  = "AllowExecutionFromAPIGateway"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.trigger_otp_lambda.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.lambda_http_api.execution_arn}/*/*"
}

resource "aws_apigatewayv2_integration" "lambda_integration" {
  api_id                 = aws_apigatewayv2_api.lambda_http_api.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.trigger_otp_lambda.invoke_arn
  integration_method     = "POST"
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "lambda_route" {
  api_id    = aws_apigatewayv2_api.lambda_http_api.id
  route_key = "POST /trigger-otp"
  target    = "integrations/${aws_apigatewayv2_integration.lambda_integration.id}"
}

resource "aws_apigatewayv2_stage" "lambda_stage" {
  api_id      = aws_apigatewayv2_api.lambda_http_api.id
  name        = "$default"
  auto_deploy = true
}

# -------------------------------
# 5. Output the API URL
# -------------------------------
output "api_gateway_url" {
  description = "Public URL to trigger the Lambda"
  value       = "${aws_apigatewayv2_api.lambda_http_api.api_endpoint}/trigger-otp"
}
