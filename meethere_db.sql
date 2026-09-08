/*
Navicat MySQL Data Transfer

Source Server         : localhost_3306
Source Server Version : 80013
Source Host           : localhost:3306
Source Database       : meethere_db

Target Server Type    : MYSQL
Target Server Version : 80013
File Encoding         : 65001

Date: 2020-01-02 22:39:17
*/

SET FOREIGN_KEY_CHECKS=0;

-- ----------------------------
-- Table structure for message
-- ----------------------------
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
  `messageID` int(11) NOT NULL AUTO_INCREMENT,
  `state` int(11) DEFAULT NULL,
  `userID` varchar(25) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `content` varchar(5000) DEFAULT NULL,
  `time` datetime DEFAULT NULL,
  PRIMARY KEY (`messageID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of message
-- ----------------------------

-- ----------------------------
-- Table structure for news
-- ----------------------------
DROP TABLE IF EXISTS `news`;
CREATE TABLE `news` (
  `newsID` int(11) NOT NULL AUTO_INCREMENT,
  `title` varchar(100) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL,
  `content` varchar(5000) DEFAULT NULL,
  `time` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`newsID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of news
-- ----------------------------
INSERT INTO `news` (`newsID`, `title`, `content`, `time`) VALUES
  (1, '场馆预约系统使用说明', '欢迎使用场馆预约系统。请先注册或登录账号，然后选择场馆和预约时间。如需帮助，请联系系统管理员。', '2026-09-08 09:00:00.000000');

-- ----------------------------
-- Table structure for order
-- ----------------------------
DROP TABLE IF EXISTS `order`;
CREATE TABLE `order` (
  `orderID` int(11) NOT NULL AUTO_INCREMENT,
  `userID` varchar(25) NOT NULL,
  `venueID` int(11) NOT NULL,
  `order_time` datetime DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `hours` int(2) DEFAULT NULL,
  `state` int(1) DEFAULT NULL,
  `total` int(5) DEFAULT NULL,
  PRIMARY KEY (`orderID`),
  KEY `userID` (`userID`),
  KEY `gymID` (`venueID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of order
-- ----------------------------

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` int(10) NOT NULL AUTO_INCREMENT,
  `userID` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL,
  `password` varchar(255) DEFAULT NULL,
  `email` varchar(255) DEFAULT NULL,
  `phone` varchar(255) DEFAULT NULL,
  `isadmin` int(10) NOT NULL,
  `user_name` varchar(255) DEFAULT NULL,
  `picture` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of user
-- ----------------------------
INSERT INTO `user` (`id`, `userID`, `password`, `email`, `phone`, `isadmin`, `user_name`, `picture`) VALUES
  (1, 'admin', 'admin', 'admin@meethere.local', '', 1, '系统管理员', '');

-- ----------------------------
-- Table structure for venue
-- ----------------------------
DROP TABLE IF EXISTS `venue`;
CREATE TABLE `venue` (
  `venueID` int(5) NOT NULL AUTO_INCREMENT,
  `description` varchar(1000) DEFAULT NULL,
  `price` int(5) DEFAULT NULL,
  `picture` varchar(255) DEFAULT NULL,
  `venue_name` varchar(255) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `close_time` varchar(255) DEFAULT NULL,
  `open_time` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`venueID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- ----------------------------
-- Records of venue
-- ----------------------------
INSERT INTO `venue` (`venueID`, `description`, `price`, `picture`, `venue_name`, `address`, `close_time`, `open_time`) VALUES
  (1, '室内标准篮球场，配备基础照明和休息区域，适合日常训练与小型比赛。', 200, '', '篮球馆', '校内体育中心一层', '21:00', '08:00'),
  (2, '室内羽毛球场，场地通风良好，可用于日常锻炼和羽毛球活动。', 100, '', '羽毛球馆', '校内体育中心二层', '21:00', '08:00');

SET FOREIGN_KEY_CHECKS=1;
