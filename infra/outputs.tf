output "ec2_public_ip" {
  value = aws_eip.ec2_1.public_ip
}

output "ec2_instance_id" {
  value = aws_instance.ec2_1.id
}

output "rds_endpoint" {
  value = aws_db_instance.this.address
}
